package dev.loat.msmp_vision.render;

import dev.loat.msmp_vision.raster.Framebuffer;
import dev.loat.msmp_vision.world.EnvironmentSnapshot;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Fills the framebuffer with a sky gradient, sun and moon. The horizon colour equals the fog colour so that
 * distant terrain blends seamlessly into the sky; the colour overhead is the sky colour of the environment.
 * Both already contain the time of day, so they are used as they are.
 */
public final class SkyPainter {

    /** Half edge length of the visible sun, as {@code tan(angle)}; measured on a client screenshot. */
    private static final float SUN_HALF = 0.075f;
    private static final float MOON_HALF = 0.05f;
    /** Half edge length of the sun quad as {@code tan(angle)}: 30 blocks at a distance of 100. */
    private static final float SUN_QUAD_HALF = 0.3f;
    /** Cosine of the angle beyond which the glow around the sun is zero. */
    private static final float GLOW_COS = (float) Math.cos(Math.toRadians(SkyColors.GLOW_RADIUS_DEGREES));

    /** Rows per parallel job. */
    private static final int BAND_ROWS = 32;

    private SkyPainter() {}

    /**
     * Paints the sky and clears depth. The image is split into bands of rows that are painted in parallel.
     *
     * @param target The framebuffer.
     * @param camera The camera.
     * @param environment Environment values.
     * @param executor Worker pool.
     * @param timeoutSeconds Maximum time for the whole sky.
     */
    public static void paint(Framebuffer target, Camera camera, EnvironmentSnapshot environment, RenderExecutor executor, long timeoutSeconds) {
        paint(target, camera, environment, executor, timeoutSeconds, null);
    }

    /**
     * Paints the sky and clears depth, using the sun texture if there is one.
     *
     * @param target The framebuffer.
     * @param camera The camera.
     * @param environment Environment values.
     * @param executor Worker pool.
     * @param timeoutSeconds Maximum time for the whole sky.
     * @param sunTexture The sun texture, {@code null} for the analytic sun and glow.
     */
    public static void paint(Framebuffer target, Camera camera, EnvironmentSnapshot environment, RenderExecutor executor, long timeoutSeconds,
                             SunTexture.Texture sunTexture) {
        Arrays.fill(target.depth(), 0f);

        float[] horizon = rgb(environment.fogColor());
        float[] zenith = rgb(environment.skyColor());

        double angle = environment.sunAngleRadians();
        Vector3f sun = new Vector3f((float) -Math.sin(angle), (float) Math.cos(angle), 0).normalize();
        Vector3f tangent = new Vector3f((float) Math.cos(angle), (float) Math.sin(angle), 0);
        float glowScale = environment.skyLightFactor();

        int height = target.height();
        List<CompletableFuture<Void>> jobs = new ArrayList<>();
        for (int from = 0; from < height; from += BAND_ROWS) {
            int start = from;
            int end = Math.min(height, from + BAND_ROWS);
            jobs.add(CompletableFuture.runAsync(
                () -> paintRows(target, camera, horizon, zenith, sun, tangent, glowScale, sunTexture, start, end), executor.executor()));
        }
        executor.awaitAll(jobs, timeoutSeconds);
    }

    private static void paintRows(Framebuffer target, Camera camera, float[] horizon, float[] zenith,
                                  Vector3f sun, Vector3f tangent, float glowScale, SunTexture.Texture sunTexture, int fromRow, int toRow) {
        int width = target.width();
        int height = target.height();
        int[] color = target.color();
        Vector3f dir = new Vector3f();
        for (int y = fromRow; y < toRow; y++) {
            float ndcY = 1f - 2f * (y + 0.5f) / height;
            for (int x = 0; x < width; x++) {
                float ndcX = 2f * (x + 0.5f) / width - 1f;
                dir.set(camera.forward())
                    .fma(ndcX * camera.tanHalfFov() * camera.aspect(), camera.right())
                    .fma(ndcY * camera.tanHalfFov(), camera.up())
                    .normalize();

                float t = SkyColors.blendBySine(dir.y);
                float r = horizon[0] + (zenith[0] - horizon[0]) * t;
                float g = horizon[1] + (zenith[1] - horizon[1]) * t;
                float b = horizon[2] + (zenith[2] - horizon[2]) * t;

                float d = dir.dot(sun);
                if (sunTexture != null) {
                    if (d > 0.01f && dir.y > -0.1f) {
                        float u = dir.dot(tangent) / d;
                        float v = dir.z / d;
                        if (Math.abs(u) < SUN_QUAD_HALF && Math.abs(v) < SUN_QUAD_HALF) {
                            int tx = Math.min(sunTexture.width() - 1, (int) ((u / SUN_QUAD_HALF * 0.5f + 0.5f) * sunTexture.width()));
                            int ty = Math.min(sunTexture.height() - 1, (int) ((v / SUN_QUAD_HALF * 0.5f + 0.5f) * sunTexture.height()));
                            int texel = sunTexture.rgb()[ty * sunTexture.width() + tx];
                            r += (texel >> 16) & 0xFF;
                            g += (texel >> 8) & 0xFF;
                            b += texel & 0xFF;
                        }
                    } else if (d < -0.01f && dir.y > -0.02f && inQuad(dir, tangent, -d, MOON_HALF)) {
                        r = 225; g = 228; b = 238;
                    }
                } else if (d > GLOW_COS) {
                    float glow = SkyColors.glow((float) Math.toDegrees(Math.acos(Math.min(1f, d)))) * glowScale;
                    r += glow;
                    g += glow;
                    b += glow;
                }

                if (sunTexture == null && dir.y > -0.02f) {
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

    private static float[] rgb(int packed) {
        return new float[]{(packed >> 16) & 0xFF, (packed >> 8) & 0xFF, packed & 0xFF};
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, (int) v));
    }
}
