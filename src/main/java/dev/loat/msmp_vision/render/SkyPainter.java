package dev.loat.msmp_vision.render;

import dev.loat.msmp_vision.raster.Framebuffer;
import dev.loat.msmp_vision.world.EnvironmentSnapshot;
import org.joml.Vector3f;

import java.util.Arrays;

/**
 * Fills the framebuffer with a sky gradient, sun and moon. The horizon colour equals the fog colour so that
 * distant terrain blends seamlessly into the sky; the colour overhead is the sky colour of the environment.
 * Both already contain the time of day, so they are used as they are.
 */
public final class SkyPainter {

    /** Distance of the horizon haze: sky blends from fog colour to sky colour over this much of {@code dir.y}. */
    private static final float HAZE_HEIGHT = 0.45f;
    /** Half edge length of the sun / moon quad, as {@code tan(angle)}; vanilla uses a 30 unit quad at 100 units. */
    private static final float SUN_HALF = 0.15f;
    private static final float MOON_HALF = 0.10f;

    private SkyPainter() {}

    /**
     * Paints the sky and clears depth.
     *
     * @param target The framebuffer.
     * @param camera The camera.
     * @param environment Environment values.
     */
    public static void paint(Framebuffer target, Camera camera, EnvironmentSnapshot environment) {
        int width = target.width();
        int height = target.height();
        int[] color = target.color();
        Arrays.fill(target.depth(), 0f);

        float[] horizon = rgb(environment.fogColor());
        float[] zenith = rgb(environment.skyColor());

        double angle = environment.sunAngleRadians();
        Vector3f sun = new Vector3f((float) -Math.sin(angle), (float) Math.cos(angle), 0).normalize();
        Vector3f tangent = new Vector3f((float) Math.cos(angle), (float) Math.sin(angle), 0);

        Vector3f dir = new Vector3f();
        for (int y = 0; y < height; y++) {
            float ndcY = 1f - 2f * (y + 0.5f) / height;
            for (int x = 0; x < width; x++) {
                float ndcX = 2f * (x + 0.5f) / width - 1f;
                dir.set(camera.forward())
                    .fma(ndcX * camera.tanHalfFov() * camera.aspect(), camera.right())
                    .fma(ndcY * camera.tanHalfFov(), camera.up())
                    .normalize();

                float t = smoothstep(Math.max(dir.y, 0f) / HAZE_HEIGHT);
                float r = horizon[0] + (zenith[0] - horizon[0]) * t;
                float g = horizon[1] + (zenith[1] - horizon[1]) * t;
                float b = horizon[2] + (zenith[2] - horizon[2]) * t;

                if (dir.y > -0.02f) {
                    float d = dir.dot(sun);
                    if (d > 0.01f && inQuad(dir, tangent, d, SUN_HALF)) {
                        r = 255; g = 255; b = 255;
                    } else if (d < -0.01f && inQuad(dir, tangent, -d, MOON_HALF)) {
                        r = 225; g = 228; b = 238;
                    }
                }
                color[y * width + x] = 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
            }
        }
    }

    /** Tests whether a view direction hits a square billboard facing the sun axis, projected onto the plane at distance 1. */
    private static boolean inQuad(Vector3f dir, Vector3f tangent, float axisDot, float half) {
        float u = dir.dot(tangent) / axisDot;
        float v = dir.z / axisDot;
        return Math.abs(u) < half && Math.abs(v) < half;
    }

    private static float smoothstep(float x) {
        float t = Math.max(0f, Math.min(1f, x));
        return t * t * (3f - 2f * t);
    }

    private static float[] rgb(int packed) {
        return new float[]{(packed >> 16) & 0xFF, (packed >> 8) & 0xFF, packed & 0xFF};
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, (int) v));
    }
}
