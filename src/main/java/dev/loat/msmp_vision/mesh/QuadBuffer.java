package dev.loat.msmp_vision.mesh;

import java.util.Arrays;


/**
 * Growable struct-of-arrays storage for lit, tinted quads in camera-relative coordinates.
 */
public final class QuadBuffer {

    private float[] positions = new float[12 * 64];
    private float[] uvs = new float[8 * 64];
    private int[] colors = new int[64];
    private int[] textures = new int[64];
    private int count;

    /**
     * Appends a quad.
     *
     * @param pos 12 floats, block-local vertex positions.
     * @param offsetX X offset added to every vertex.
     * @param offsetY Y offset added to every vertex.
     * @param offsetZ Z offset added to every vertex.
     * @param uv 8 floats of texture coordinates.
     * @param texture Texture id.
     * @param rgb Vertex colour multiplier as {@code 0xRRGGBB}.
     */
    public void add(
        float[] pos,
        float offsetX,
        float offsetY,
        float offsetZ,
        float[] uv,
        int texture,
        int rgb
    ) {
        if (this.count == this.textures.length) {
            int capacity = this.count * 2;
            this.positions = Arrays.copyOf(this.positions, capacity * 12);
            this.uvs = Arrays.copyOf(this.uvs, capacity * 8);
            this.colors = Arrays.copyOf(this.colors, capacity);
            this.textures = Arrays.copyOf(this.textures, capacity);
        }
        int p = this.count * 12;
        for (int v = 0; v < 4; v++) {
            this.positions[p + v * 3] = pos[v * 3] + offsetX;
            this.positions[p + v * 3 + 1] = pos[v * 3 + 1] + offsetY;
            this.positions[p + v * 3 + 2] = pos[v * 3 + 2] + offsetZ;
        }
        System.arraycopy(uv, 0, this.uvs, this.count * 8, 8);
        this.colors[this.count] = rgb;
        this.textures[this.count] = texture;
        this.count++;
    }

    /** @return Number of quads. */
    public int count() { return this.count; }

    /** @return Vertex positions, 12 floats per quad. */
    public float[] positions() { return this.positions; }

    /** @return Texture coordinates, 8 floats per quad. */
    public float[] uvs() { return this.uvs; }

    /**
     * @param quad Quad index.
     * @return Colour multiplier as {@code 0xRRGGBB}.
     */
    public int color(int quad) { return this.colors[quad]; }

    /**
     * @param quad Quad index.
     * @return Texture id.
     */
    public int texture(int quad) { return this.textures[quad]; }
}
