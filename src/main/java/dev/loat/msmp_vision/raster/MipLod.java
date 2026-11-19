package dev.loat.msmp_vision.raster;

/**
 * Level of detail for mipmapping, computed like a GPU does: from how many texels one screen pixel covers.
 */
public final class MipLod {

    private MipLod() {}

    /**
     * Computes the level of detail of a pixel.
     *
     * @param dudx Change of the horizontal texture coordinate per pixel to the right.
     * @param dvdx Change of the vertical texture coordinate per pixel to the right.
     * @param dudy Change of the horizontal texture coordinate per pixel downwards.
     * @param dvdy Change of the vertical texture coordinate per pixel downwards.
     * @param textureWidth Width of the full-size texture in pixels.
     * @param textureHeight Height of the full-size texture in pixels.
     * @return 0 if a pixel covers one texel or less (magnification), otherwise {@code log2} of the texels covered by the
     * longer side of the pixel's footprint.
     */
    public static float lod(float dudx, float dvdx, float dudy, float dvdy, int textureWidth, int textureHeight) {
        float xu = dudx * textureWidth;
        float xv = dvdx * textureHeight;
        float yu = dudy * textureWidth;
        float yv = dvdy * textureHeight;
        float squared = Math.max(xu * xu + xv * xv, yu * yu + yv * yv);
        if (squared <= 1f) {
            return 0f;
        }
        return 0.5f * log2(squared);
    }

    /**
     * Fast {@code log2} for values {@code >= 1}, accurate to about 0.01.
     *
     * @param x The value.
     * @return Its base 2 logarithm.
     */
    public static float log2(float x) {
        int bits = Float.floatToRawIntBits(x);
        int exponent = (bits >> 23) - 127;
        float mantissa = Float.intBitsToFloat((bits & 0x007FFFFF) | 0x3F800000) - 1f;
        return exponent + mantissa * (1.3465f - 0.3465f * mantissa);
    }
}
