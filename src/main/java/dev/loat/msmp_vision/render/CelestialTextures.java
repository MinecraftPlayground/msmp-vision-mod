package dev.loat.msmp_vision.render;

import dev.loat.msmp_vision.assets.AssetManager;
import dev.loat.msmp_vision.assets.ImageDecoder;
import dev.loat.msmp_vision.logging.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The textures of the sun and of the eight moon phases. Minecraft draws them as additively blended, unfiltered quads:
 * the bright centre is only 8 of their 32 texels wide, the rest is the pixelated glow around it.
 *
 * <p>The files moved between versions ({@code environment/sun.png}, {@code environment/celestial/sun.png}, ...), so
 * they are looked up by file name. Loaded lazily on first use.</p>
 */
public final class CelestialTextures {

    /**
     * @param rgb Texel colours as {@code 0xRRGGBB}, row-major.
     * @param width Width in texels.
     * @param height Height in texels.
     */
    public record Texture(int[] rgb, int width, int height) {

        /**
         * Nearest-neighbour lookup.
         *
         * @param u Horizontal coordinate, 0..1.
         * @param v Vertical coordinate (0 = top), 0..1.
         * @return The colour as {@code 0xRRGGBB}.
         */
        public int sample(float u, float v) {
            int x = Math.max(0, Math.min(this.width - 1, (int) (u * this.width)));
            int y = Math.max(0, Math.min(this.height - 1, (int) (v * this.height)));
            return this.rgb[y * this.width + x];
        }
    }

    private static final Texture MISSING = new Texture(new int[0], 0, 0);

    private final AssetManager assets;
    private final ConcurrentHashMap<String, Texture> cache = new ConcurrentHashMap<>();

    /**
     * @param assets The asset source.
     */
    public CelestialTextures(AssetManager assets) {
        this.assets = assets;
    }

    /**
     * @return The sun texture, or {@code null} if it is missing (the sky then uses an analytic sun and glow).
     */
    public Texture sun() {
        return this.get("/sun.png");
    }

    /**
     * @param phase File name of the phase without extension, ex. {@code full_moon}.
     * @return The moon texture, or {@code null} if it is missing.
     */
    public Texture moon(String phase) {
        return this.get("/moon/" + phase + ".png");
    }

    private Texture get(String suffix) {
        Texture texture = this.cache.computeIfAbsent(suffix, this::load);
        return texture == MISSING ? null : texture;
    }

    private Texture load(String suffix) {
        String path = this.assets.find(suffix);
        byte[] bytes = path == null ? null : this.assets.read(path);
        if (bytes == null) {
            Logger.warning("Texture '%s' not found.".formatted(suffix));
            return MISSING;
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image != null) {
                int[] argb = ImageDecoder.argb(image);
                int[] rgb = new int[argb.length];
                for (int i = 0; i < rgb.length; i++) {
                    rgb[i] = argb[i] & 0xFFFFFF;
                }
                return new Texture(rgb, image.getWidth(), image.getHeight());
            }
        } catch (IOException e) {
            Logger.warning("Failed to read '%s': %s".formatted(path, e.getMessage()));
        }
        return MISSING;
    }
}
