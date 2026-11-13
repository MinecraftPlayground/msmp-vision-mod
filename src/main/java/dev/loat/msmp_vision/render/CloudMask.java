package dev.loat.msmp_vision.render;

import dev.loat.msmp_vision.assets.AssetManager;
import dev.loat.msmp_vision.assets.ResourceIds;
import dev.loat.msmp_vision.logging.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * The cloud pattern: the alpha channel of {@code textures/environment/clouds.png} tells which 12x12 block cells
 * contain a cloud. Loaded lazily on first use.
 */
public final class CloudMask {

    /**
     * @param cells Which cells contain a cloud, row-major.
     * @param width Width in cells.
     * @param height Height in cells.
     */
    public record Mask(boolean[] cells, int width, int height) {}

    private static final String TEXTURE = "minecraft:environment/clouds";

    private final AssetManager assets;
    private Mask mask;
    private boolean tried;

    /**
     * @param assets The asset source.
     */
    public CloudMask(AssetManager assets) {
        this.assets = assets;
    }

    /**
     * Returns the pattern.
     *
     * @return The mask, or {@code null} if the texture is missing or unreadable (clouds are then skipped).
     */
    public synchronized Mask get() {
        if (!this.tried) {
            this.tried = true;
            this.mask = this.load();
        }
        return this.mask;
    }

    private Mask load() {
        byte[] bytes = this.assets.read(ResourceIds.texturePath(TEXTURE));
        if (bytes == null) {
            Logger.warning("Cloud texture '%s' not found, clouds are disabled.".formatted(TEXTURE));
            return null;
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                Logger.warning("Cloud texture '%s' could not be decoded, clouds are disabled.".formatted(TEXTURE));
                return null;
            }
            int width = image.getWidth();
            int height = image.getHeight();
            boolean[] cells = new boolean[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    cells[y * width + x] = (image.getRGB(x, y) >>> 24) > 0;
                }
            }
            return new Mask(cells, width, height);
        } catch (IOException e) {
            Logger.warning("Failed to read the cloud texture: " + e.getMessage());
            return null;
        }
    }
}
