package dev.loat.msmp_vision.assets;

/**
 * A decoded texture (first animation frame only) as ARGB pixels, together with its mipmap levels.
 *
 * <p>The mipmaps are generated like Minecraft does: every level is the average of 2x2 pixels of the previous one,
 * computed in linear light (gamma 2.2) and weighted by alpha, so that transparent pixels do not darken the colour
 * of their neighbours. There are at most {@value #MAX_MIPMAP_LEVELS} levels besides the full-size one.</p>
 */
public final class TextureData {

    /** Maximum number of mipmap levels besides the original, like Minecraft's "Mipmap Levels" option. */
    public static final int MAX_MIPMAP_LEVELS = 4;

    private static final float GAMMA = 2.2f;
    private static final float[] TO_LINEAR = new float[256];

    static {
        for (int i = 0; i < 256; i++) {
            TO_LINEAR[i] = (float) Math.pow(i / 255.0, GAMMA);
        }
    }

    private final int id;
    private final int width;
    private final int height;
    private final boolean translucent;
    private final int[][] levelPixels;
    private final int[] levelWidths;
    private final int[] levelHeights;

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
        this.translucent = translucent;

        int levels = 1;
        int w = width;
        int h = height;
        while (levels <= MAX_MIPMAP_LEVELS && (w > 1 || h > 1)) {
            w = Math.max(1, w / 2);
            h = Math.max(1, h / 2);
            levels++;
        }
        this.levelPixels = new int[levels][];
        this.levelWidths = new int[levels];
        this.levelHeights = new int[levels];
        this.levelPixels[0] = pixels;
        this.levelWidths[0] = width;
        this.levelHeights[0] = height;
        for (int level = 1; level < levels; level++) {
            this.levelWidths[level] = Math.max(1, this.levelWidths[level - 1] / 2);
            this.levelHeights[level] = Math.max(1, this.levelHeights[level - 1] / 2);
            this.levelPixels[level] = downsample(this.levelPixels[level - 1], this.levelWidths[level - 1], this.levelHeights[level - 1],
                this.levelWidths[level], this.levelHeights[level]);
        }
    }

    /**
     * Returns the store index.
     *
     * @return The id.
     */
    public int id() {
        return this.id;
    }

    /** @return Width of the full-size texture in pixels. */
    public int width() {
        return this.width;
    }

    /** @return Height of the full-size texture in pixels. */
    public int height() {
        return this.height;
    }

    /** @return Number of levels including the full-size one. */
    public int levels() {
        return this.levelPixels.length;
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
     * Samples the full-size texture with nearest-neighbour filtering and repeat wrapping.
     *
     * @param u Horizontal coordinate in [0, 1].
     * @param v Vertical coordinate in [0, 1].
     * @return The ARGB pixel.
     */
    public int sample(float u, float v) {
        return this.sampleLevel(0, u, v);
    }

    /**
     * Samples with mipmapping like OpenGL's {@code NEAREST_MIPMAP_LINEAR}: the nearest texel of the two levels
     * around {@code lod}, blended by the fractional part.
     *
     * @param u Horizontal coordinate in [0, 1].
     * @param v Vertical coordinate in [0, 1].
     * @param lod Level of detail, 0 = full size, 1 = half size, ...
     * @param maxLevel Highest level that may be used (0 turns mipmapping off).
     * @return The ARGB pixel.
     */
    public int sample(float u, float v, float lod, int maxLevel) {
        int top = Math.min(maxLevel, this.levelPixels.length - 1);
        if (lod <= 0f || top <= 0) {
            return this.sampleLevel(0, u, v);
        }
        if (lod >= top) {
            return this.sampleLevel(top, u, v);
        }
        int level = (int) lod;
        int fraction = (int) ((lod - level) * 256f);
        int a = this.sampleLevel(level, u, v);
        if (fraction == 0) {
            return a;
        }
        int b = this.sampleLevel(level + 1, u, v);
        return mix(a, b, fraction);
    }

    private int sampleLevel(int level, float u, float v) {
        int w = this.levelWidths[level];
        int h = this.levelHeights[level];
        int x = Math.floorMod((int) Math.floor((double) u * w), w);
        int y = Math.floorMod((int) Math.floor((double) v * h), h);
        return this.levelPixels[level][y * w + x];
    }

    /** Blends two pixels weighted by alpha, so the (meaningless) colour of a transparent pixel does not darken the edge. */
    private static int mix(int a, int b, int fraction) {
        int alphaA = a >>> 24;
        int alphaB = b >>> 24;
        int weightA = alphaA * (256 - fraction);
        int weightB = alphaB * fraction;
        int total = weightA + weightB;
        if (total == 0) {
            return 0;
        }
        int result = ((alphaA * (256 - fraction) + alphaB * fraction) >> 8) << 24;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int ca = (a >>> shift) & 0xFF;
            int cb = (b >>> shift) & 0xFF;
            result |= ((ca * weightA + cb * weightB) / total) << shift;
        }
        return result;
    }

    private static int[] downsample(int[] source, int sw, int sh, int dw, int dh) {
        int[] result = new int[dw * dh];
        for (int y = 0; y < dh; y++) {
            for (int x = 0; x < dw; x++) {
                int x0 = Math.min(sw - 1, x * 2);
                int x1 = Math.min(sw - 1, x * 2 + 1);
                int y0 = Math.min(sh - 1, y * 2);
                int y1 = Math.min(sh - 1, y * 2 + 1);
                result[y * dw + x] = average(source[y0 * sw + x0], source[y0 * sw + x1], source[y1 * sw + x0], source[y1 * sw + x1]);
            }
        }
        return result;
    }

    /** Alpha weighted average of four pixels in linear light. */
    private static int average(int a, int b, int c, int d) {
        float alphaSum = 0f;
        float red = 0f;
        float green = 0f;
        float blue = 0f;
        for (int pixel : new int[]{a, b, c, d}) {
            float alpha = (pixel >>> 24) / 255f;
            alphaSum += alpha;
            red += alpha * TO_LINEAR[(pixel >> 16) & 0xFF];
            green += alpha * TO_LINEAR[(pixel >> 8) & 0xFF];
            blue += alpha * TO_LINEAR[pixel & 0xFF];
        }
        if (alphaSum <= 0f) {
            return 0;
        }
        int alphaOut = Math.round(alphaSum / 4f * 255f);
        return (alphaOut << 24) | (toSrgb(red / alphaSum) << 16) | (toSrgb(green / alphaSum) << 8) | toSrgb(blue / alphaSum);
    }

    private static int toSrgb(float linear) {
        return Math.max(0, Math.min(255, Math.round((float) Math.pow(Math.max(0f, linear), 1.0 / GAMMA) * 255f)));
    }
}
