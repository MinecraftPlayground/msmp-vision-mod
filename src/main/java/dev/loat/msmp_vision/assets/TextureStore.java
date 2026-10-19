package dev.loat.msmp_vision.assets;

import dev.loat.msmp_vision.logging.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, lazily filled registry of textures. Every texture gets a stable integer id
 * which is used by meshes and the rasterizer.
 */
public final class TextureStore {

    private final AssetManager assets;
    private final ConcurrentHashMap<String, TextureData> byName = new ConcurrentHashMap<>();
    private final List<TextureData> registry = new ArrayList<>();

    /**
     * Creates a store.
     *
     * @param assets The asset source.
     */
    public TextureStore(AssetManager assets) {
        this.assets = assets;
    }

    /**
     * Returns the texture for an identifier, loading it on first use.
     * A magenta/black placeholder is returned if the texture is missing.
     *
     * @param reference Texture identifier, e.g. {@code minecraft:block/stone} or {@code block/stone}.
     * @return The texture, never {@code null}.
     */
    public TextureData get(String reference) {
        String name = ResourceIds.normalize(reference);
        TextureData existing = this.byName.get(name);
        if (existing != null) {
            return existing;
        }
        return this.byName.computeIfAbsent(name, this::load);
    }

    /**
     * Returns all registered textures indexed by id. Call after meshing so every id is known.
     *
     * @return A copy of the registry.
     */
    public TextureData[] snapshot() {
        synchronized (this.registry) {
            return this.registry.toArray(new TextureData[0]);
        }
    }

    private TextureData load(String name) {
        byte[] bytes = this.assets.read(ResourceIds.texturePath(name));
        if (bytes != null) {
            try {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
                if (image != null) {
                    int width = image.getWidth();
                    int frameHeight = Math.min(image.getHeight(), width); // animated strips: first frame only
                    return this.register(width, frameHeight, image.getRGB(0, 0, width, frameHeight, null, 0, width));
                }
            } catch (IOException e) {
                Logger.warning("Failed to decode texture '%s': %s".formatted(name, e.getMessage()));
            }
        } else {
            Logger.debug("Missing texture '%s'.".formatted(name));
        }
        return this.register(16, 16, missingPixels());
    }

    private TextureData register(int width, int height, int[] pixels) {
        boolean translucent = false;
        for (int argb : pixels) {
            int alpha = argb >>> 24;
            if (alpha > 0 && alpha < 255) {
                translucent = true;
                break;
            }
        }
        synchronized (this.registry) {
            TextureData texture = new TextureData(this.registry.size(), width, height, pixels, translucent);
            this.registry.add(texture);
            return texture;
        }
    }

    private static int[] missingPixels() {
        int[] pixels = new int[16 * 16];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                pixels[y * 16 + x] = ((x / 8 + y / 8) % 2 == 0) ? 0xFFF800F8 : 0xFF000000;
            }
        }
        return pixels;
    }
}
