package dev.loat.msmp_vision.raster;

import java.util.Arrays;
import java.util.List;


/**
 * Screen-space triangles. Per vertex 8 floats: {@code x, y, 1/w, u/w, v/w, r, g, b}.
 */
public final class TriangleBuffer {

    /** Floats per vertex. */
    public static final int VERTEX = 8;
    /** Floats per triangle. */
    public static final int TRIANGLE = VERTEX * 3;

    private float[] data = new float[TRIANGLE * 64];
    private int[] textures = new int[64];
    private float[] depths = new float[64];
    private int count;

    /**
     * Appends a triangle.
     *
     * @param texture Texture id.
     * @param a First vertex.
     * @param b Second vertex.
     * @param c Third vertex.
     * @param depth Average view depth, used for sorting.
     */
    public void add(int texture, float[] a, float[] b, float[] c, float depth) {
        if (this.count == this.textures.length) {
            int capacity = this.count * 2;
            this.data = Arrays.copyOf(this.data, capacity * TRIANGLE);
            this.textures = Arrays.copyOf(this.textures, capacity);
            this.depths = Arrays.copyOf(this.depths, capacity);
        }
        int o = this.count * TRIANGLE;
        System.arraycopy(a, 0, this.data, o, VERTEX);
        System.arraycopy(b, 0, this.data, o + VERTEX, VERTEX);
        System.arraycopy(c, 0, this.data, o + 2 * VERTEX, VERTEX);
        this.textures[this.count] = texture;
        this.depths[this.count] = depth;
        this.count++;
    }

    /** @return Triangle count. */
    public int count() { return this.count; }

    /** @return Vertex data. */
    public float[] data() { return this.data; }

    /**
     * @param triangle Triangle index.
     * @return Texture id.
     */
    public int texture(int triangle) { return this.textures[triangle]; }

    /**
     * @param triangle Triangle index.
     * @return Average view depth.
     */
    public float depth(int triangle) { return this.depths[triangle]; }

    /**
     * Concatenates buffers.
     *
     * @param buffers The buffers.
     * @return A new buffer containing all triangles.
     */
    public static TriangleBuffer merge(List<TriangleBuffer> buffers) {
        int total = 0;
        for (TriangleBuffer buffer : buffers) {
            total += buffer.count;
        }
        TriangleBuffer merged = new TriangleBuffer();
        merged.data = new float[Math.max(1, total) * TRIANGLE];
        merged.textures = new int[Math.max(1, total)];
        merged.depths = new float[Math.max(1, total)];
        for (TriangleBuffer buffer : buffers) {
            System.arraycopy(buffer.data, 0, merged.data, merged.count * TRIANGLE, buffer.count * TRIANGLE);
            System.arraycopy(buffer.textures, 0, merged.textures, merged.count, buffer.count);
            System.arraycopy(buffer.depths, 0, merged.depths, merged.count, buffer.count);
            merged.count += buffer.count;
        }
        return merged;
    }
}
