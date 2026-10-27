package dev.loat.msmp_vision.raster;

/**
 * Linear, spherical distance fog.
 *
 * <p>The distance is the real distance from the camera to the fragment, not just the depth along the view axis,
 * so the fog forms a sphere around the camera like in Minecraft instead of a flat wall in front of it.</p>
 */
public final class Fog {

    private final float start;
    private final float end;
    private final int r;
    private final int g;
    private final int b;
    private final float tanX;
    private final float tanY;
    private final float pixelToNdcX;
    private final float pixelToNdcY;
    private final float maxScale;

    private Fog(float start, float end, int r, int g, int b, float tanX, float tanY, int width, int height) {
        this.start = start;
        this.end = end;
        this.r = r;
        this.g = g;
        this.b = b;
        this.tanX = tanX;
        this.tanY = tanY;
        this.pixelToNdcX = 2f / width;
        this.pixelToNdcY = 2f / height;
        // Largest ratio of real distance to view depth, reached in the corners of the screen
        this.maxScale = (float) Math.sqrt(1f + tanX * tanX + tanY * tanY);
    }

    /**
     * Creates fog from a packed colour.
     *
     * @param start Distance in blocks where fog begins.
     * @param end Distance in blocks of full fog.
     * @param rgb Colour as {@code 0xRRGGBB}.
     * @param tanHalfFov {@code tan(fov / 2)} of the vertical field of view.
     * @param aspect Width divided by height.
     * @param width Framebuffer width.
     * @param height Framebuffer height.
     * @return The fog.
     */
    public static Fog of(float start, float end, int rgb, float tanHalfFov, float aspect, int width, int height) {
        return new Fog(start, end, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, tanHalfFov * aspect, tanHalfFov, width, height);
    }

    /** @return Fog red. */
    public int r() { return this.r; }

    /** @return Fog green. */
    public int g() { return this.g; }

    /** @return Fog blue. */
    public int b() { return this.b; }

    /**
     * Visibility of a fragment.
     *
     * @param x Pixel column.
     * @param y Pixel row.
     * @param w View-space depth of the fragment (distance along the view axis).
     * @return Visibility 1 (no fog) to 0 (full fog).
     */
    public float factor(int x, int y, float w) {
        if (w * this.maxScale <= this.start) {
            return 1f; // even the farthest pixel of this depth is still before the fog
        }
        float nx = ((x + 0.5f) * this.pixelToNdcX - 1f) * this.tanX;
        float ny = (1f - (y + 0.5f) * this.pixelToNdcY) * this.tanY;
        float distance = w * (float) Math.sqrt(1f + nx * nx + ny * ny);
        float range = this.end - this.start;
        if (range <= 0) {
            return distance < this.end ? 1f : 0f;
        }
        return Math.max(0f, Math.min(1f, (this.end - distance) / range));
    }
}
