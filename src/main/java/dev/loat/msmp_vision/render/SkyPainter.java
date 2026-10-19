package dev.loat.msmp_vision.render;

import dev.loat.msmp_vision.raster.Framebuffer;
import dev.loat.msmp_vision.world.EnvironmentSnapshot;
import org.joml.Vector3f;

import java.util.Arrays;

/**
 * Fills the framebuffer with a sky gradient, sun and moon. The horizon colour equals the fog colour so that
 * distant terrain blends seamlessly into the sky.
 */
public final class SkyPainter {

    private static final int ZENITH_DAY = 0x78A7FF;
    private static final float SUN_COS = 0.997f;
    private static final float MOON_COS = 0.9985f;

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

        float day = Math.max(environment.skyLightFactor(), 0.03f);
        float[] horizon = rgb(environment.fogColor());
        float[] zenith = {((ZENITH_DAY >> 16) & 0xFF) * day, ((ZENITH_DAY >> 8) & 0xFF) * day, (ZENITH_DAY & 0xFF) * day};
        Vector3f sun = new Vector3f((float) -Math.sin(environment.sunAngleRadians()), (float) Math.cos(environment.sunAngleRadians()), 0).normalize();

        Vector3f dir = new Vector3f();
        for (int y = 0; y < height; y++) {
            float ndcY = 1f - 2f * (y + 0.5f) / height;
            for (int x = 0; x < width; x++) {
                float ndcX = 2f * (x + 0.5f) / width - 1f;
                dir.set(camera.forward())
                    .fma(ndcX * camera.tanHalfFov() * camera.aspect(), camera.right())
                    .fma(ndcY * camera.tanHalfFov(), camera.up())
                    .normalize();
                float t = (float) Math.sqrt(Math.max(dir.y, 0f));
                float r = horizon[0] + (zenith[0] - horizon[0]) * t;
                float g = horizon[1] + (zenith[1] - horizon[1]) * t;
                float b = horizon[2] + (zenith[2] - horizon[2]) * t;

                float sunDot = dir.dot(sun);
                if (sunDot > SUN_COS) {
                    r = 255; g = 235; b = 150;
                } else if (-sunDot > MOON_COS) {
                    r = 225; g = 225; b = 232;
                }
                color[y * width + x] = 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
            }
        }
    }

    private static float[] rgb(int packed) {
        return new float[]{(packed >> 16) & 0xFF, (packed >> 8) & 0xFF, packed & 0xFF};
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, (int) v));
    }
}
