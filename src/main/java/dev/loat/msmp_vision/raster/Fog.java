package dev.loat.msmp_vision.raster;

/**
 * Linear distance fog.
 *
 * <p>The distance is the real distance from the camera to the fragment, not just the depth along the view axis,
 * so the fog forms a sphere around the camera like in Minecraft instead of a flat wall in front of it.</p>
 *
 * <p>With {@link #withEnvironment} it works like Minecraft's terrain fog: the environmental fog (a faint haze that
 * starts at the camera) uses the spherical distance, the render distance fog uses the cylindrical distance
 * ({@code max(horizontal distance, vertical distance)}), and the stronger of both applies.</p>
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
    private final boolean environmental;
    private final float environmentStart;
    private final float environmentEnd;
    private final float[] basis;

    private Fog(float start, float end, int r, int g, int b, float tanX, float tanY, int width, int height,
                boolean environmental, float environmentStart, float environmentEnd, float[] basis) {
        this.environmental = environmental;
        this.environmentStart = environmentStart;
        this.environmentEnd = environmentEnd;
        this.basis = basis;
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
        return new Fog(start, end, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, tanHalfFov * aspect, tanHalfFov, width, height, false, 0f, 0f, null);
    }

    /**
     * Returns a copy that adds the environmental fog and measures the render distance fog cylindrically.
     *
     * @param environmentStart Distance in blocks where the environmental fog begins.
     * @param environmentEnd Distance in blocks of full environmental fog.
     * @param forward Camera view direction (3 floats).
     * @param right Camera right vector (3 floats).
     * @param up Camera up vector (3 floats).
     * @return The new fog.
     */
    public Fog withEnvironment(float environmentStart, float environmentEnd, float[] forward, float[] right, float[] up) {
        float[] basis = {forward[0], forward[1], forward[2], right[0], right[1], right[2], up[0], up[1], up[2]};
        return new Fog(this.start, this.end, this.r, this.g, this.b, this.tanX, this.tanY,
            Math.round(2f / this.pixelToNdcX), Math.round(2f / this.pixelToNdcY), true, environmentStart, environmentEnd, basis);
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
        if (this.environmental) {
            return this.environmentalFactor(x, y, w);
        }
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

    private float environmentalFactor(int x, int y, float w) {
        float nx = ((x + 0.5f) * this.pixelToNdcX - 1f) * this.tanX;
        float ny = (1f - (y + 0.5f) * this.pixelToNdcY) * this.tanY;
        float sphere = w * (float) Math.sqrt(1f + nx * nx + ny * ny);
        float environment = 0f;
        float span = this.environmentEnd - this.environmentStart;
        if (span > 0f) {
            environment = Math.max(0f, Math.min(1f, (sphere - this.environmentStart) / span));
        }
        float renderDistance = 0f;
        if (w * this.maxScale > this.start) { // the cylindrical distance is never larger than the spherical one
            float[] a = this.basis;
            float dx = a[0] + nx * a[3] + ny * a[6];
            float dy = a[1] + nx * a[4] + ny * a[7];
            float dz = a[2] + nx * a[5] + ny * a[8];
            float cylinder = w * Math.max((float) Math.sqrt(dx * dx + dz * dz), Math.abs(dy));
            float range = this.end - this.start;
            renderDistance = range <= 0 ? (cylinder < this.end ? 0f : 1f)
                : Math.max(0f, Math.min(1f, (cylinder - this.start) / range));
        }
        return 1f - Math.max(environment, renderDistance);
    }
}
