package dev.loat.msmp_vision.raster;

/**
 * Linear distance fog.
 *
 * @param start Distance where fog begins.
 * @param end Distance of full fog.
 * @param r Fog red.
 * @param g Fog green.
 * @param b Fog blue.
 */
public record Fog(
    float start,
    float end,
    int r,
    int g,
    int b
) {

    /**
     * Creates fog from a packed colour.
     *
     * @param start Start distance.
     * @param end End distance.
     * @param rgb Colour as {@code 0xRRGGBB}.
     * @return The fog.
     */
    public static Fog of(float start, float end, int rgb) {
        return new Fog(start, end, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    /**
     * @param distance View-space depth.
     * @return Visibility 1 (no fog) to 0 (full fog).
     */
    public float factor(float distance) {
        float range = this.end - this.start;
        if (range <= 0) {
            return distance < this.end ? 1f : 0f;
        }
        return Math.max(0f, Math.min(1f, (this.end - distance) / range));
    }
}
