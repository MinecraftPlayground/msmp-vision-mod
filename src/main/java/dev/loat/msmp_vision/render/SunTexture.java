package dev.loat.msmp_vision.render;

import dev.loat.msmp_vision.assets.AssetManager;
import dev.loat.msmp_vision.assets.ImageDecoder;
import dev.loat.msmp_vision.assets.ResourceIds;
import dev.loat.msmp_vision.logging.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

/**
 * The texture of the sun. Minecraft draws it as an additively blended, unfiltered quad: the bright centre is only
 * 8 of its 32 texels wide, the rest is the pixelated glow around the sun. Loaded lazily on first use.
 */
public final class SunTexture {

    /**
     * @param rgb Texel colours as {@code 0xRRGGBB}, row-major.
     * @param width Width in texels.
     * @param height Height in texels.
     */
    public record Texture(int[] rgb, int width, int height) {}

    /** Paths tried in this order (the texture moved from {@code environment} to {@code celestial}). */
    private static final List<String> CANDIDATES = List.of("minecraft:celestial/sun", "minecraft:environment/sun");

    private final AssetManager assets;
    private Texture texture;
    private boolean tried;

    /**
     * @param assets The asset source.
     */
    public SunTexture(AssetManager assets) {
        this.assets = assets;
    }

    /**
     * Returns the texture.
     *
     * @return The texture, or {@code null} if it is missing (the sky then uses an analytic glow).
     */
    public synchronized Texture get() {
        if (!this.tried) {
            this.tried = true;
            this.texture = this.load();
        }
        return this.texture;
    }

    private Texture load() {
        for (String id : CANDIDATES) {
            byte[] bytes = this.assets.read(ResourceIds.texturePath(id));
            if (bytes == null) {
                continue;
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
                Logger.warning("Failed to read the sun texture '%s': %s".formatted(id, e.getMessage()));
            }
        }
        Logger.warning("Sun texture not found, using an analytic glow.");
        return null;
    }
}
