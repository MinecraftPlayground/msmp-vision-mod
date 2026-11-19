package dev.loat.msmp_vision.render;

import dev.loat.msmp_vision.assets.TextureData;
import dev.loat.msmp_vision.encode.ImageEncoder;
import dev.loat.msmp_vision.logging.Logger;
import dev.loat.msmp_vision.mesh.QuadBuffer;
import dev.loat.msmp_vision.raster.Fog;
import dev.loat.msmp_vision.raster.Framebuffer;
import dev.loat.msmp_vision.raster.GeometryProjector;
import dev.loat.msmp_vision.raster.TileRasterizer;
import dev.loat.msmp_vision.raster.TriangleBuffer;
import dev.loat.msmp_vision.world.EnvironmentSnapshot;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Renders a synthetic scene a few times when the server starts, so that the Java JIT compiler has already optimized
 * the hot loops (projection, rasterizer, sky, PNG encoder) before the first real screenshot is requested.
 * Without it, the first screenshots after a start run in the slow interpreter and take several times longer.
 *
 * <p>The scene is a large grid of textured quads; it does not touch the world.</p>
 */
public final class Warmup {

    private static final int WIDTH = 640;
    private static final int HEIGHT = 360;
    private static final int ROUNDS = 6;

    private Warmup() {}

    /**
     * Runs the warm-up. Blocks the calling thread for about a second.
     *
     * @param executor The render pool, the same one real screenshots use.
     * @param tileSize Raster tile size from the config.
     * @param pngCompressionLevel PNG compression level from the config.
     */
    public static void run(RenderExecutor executor, int tileSize, int pngCompressionLevel) {
        long start = System.nanoTime();
        try {
            RenderSettings settings = new RenderSettings(List.of(0.5, 80.0, 0.5), 0.0, 20.0, WIDTH, HEIGHT, 90.0, 8, 1, 1, 0.5, 4);
            Camera camera = new Camera(settings);
            EnvironmentSnapshot environment = new EnvironmentSnapshot(0.4, 1f, 0xB3D0FF, 0x6EA5FF, true, 0L, false);

            int[] pixels = new int[16 * 16];
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = 0xFF000000 | (((i / 16 + i % 16) & 1) == 0 ? 0xC8C8C8 : 0x909090);
            }
            TextureData[] textures = {new TextureData(0, 16, 16, pixels, false)};

            QuadBuffer quads = new QuadBuffer();
            float[] top = {0, 0, 0, 0, 0, 1, 1, 0, 1, 1, 0, 0};
            float[] uv = {0, 0, 0, 1, 1, 1, 1, 0};
            for (int layer = 0; layer < 2; layer++) {
                for (int x = -30; x <= 30; x++) {
                    for (int z = 3; z <= 150; z++) {
                        quads.add(top, x, -3 - layer * 2, z, uv, 0, ((x + z) & 1) == 0 ? 0xFFFFFF : 0xB0B0B0);
                    }
                }
            }

            Fog fog = Fog.of(120f, 150f, environment.fogColor(), camera.tanHalfFov(), camera.aspect(), WIDTH, HEIGHT);
            TileRasterizer rasterizer = new TileRasterizer(executor, tileSize, 60, 4);
            for (int round = 0; round < ROUNDS; round++) {
                TriangleBuffer triangles = new GeometryProjector(camera.viewProjection(), WIDTH, HEIGHT, true).project(quads);
                Framebuffer framebuffer = new Framebuffer(WIDTH, HEIGHT);
                SkyPainter.paint(framebuffer, camera, environment, executor, 60);
                rasterizer.rasterize(triangles, new TriangleBuffer(), framebuffer, textures, fog);
                BufferedImage image = framebuffer.toImage(1);
                ImageEncoder.toBase64Png(image, pngCompressionLevel);
            }
            Logger.info("Renderer warm-up finished in %d ms.".formatted((System.nanoTime() - start) / 1_000_000L));
        } catch (Throwable error) {
            Logger.warning("Renderer warm-up failed (this does not affect screenshots): " + error);
        }
    }
}
