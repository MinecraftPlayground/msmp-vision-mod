package dev.loat.msmp_vision.assets;

/**
 * A decoded texture (first animation frame only) as ARGB pixels.
 */
public final class TextureData {

    private final int id;
    private final int width;
    private final int height;
    private final int[] pixels;
    private final boolean translucent;

    /**
     * Creates a texture.
     *
     * @param id Index in the {@link TextureStore}.
     * @param width Width in pixels.
     * @param height Height in pixels.
     * @param pixels ARGB pixels, row-major, {@code width * height} entries.
     * @param translucent Whether any pixel has an alpha strictly between 0 and 255.
     */
    public TextureData(int id, int width, int height, int[] pixels, boolean translucent) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.pixels = pixels;
        this.translucent = translucent;
    }

    /**
     * Returns the store index.
     *
     * @return The id.
     */
    public int id() {
        return this.id;
    }

    /**
     * Returns whether the texture needs alpha blending.
     *
     * @return {@code true} if translucent.
     */
    public boolean translucent() {
        return this.translucent;
    }

    /**
     * Samples the texture with nearest-neighbour filtering and repeat wrapping.
     *
     * @param u Horizontal coordinate in [0, 1].
     * @param v Vertical coordinate in [0, 1].
     * @return The ARGB pixel.
     */
    public int sample(float u, float v) {
        int x = Math.floorMod((int) Math.floor((double) u * this.width), this.width);
        int y = Math.floorMod((int) Math.floor((double) v * this.height), this.height);
        return this.pixels[y * this.width + x];
    }
}
