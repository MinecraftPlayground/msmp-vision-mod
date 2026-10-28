package dev.loat.msmp_vision.mesh;

import java.util.Arrays;


/**
 * Growable struct-of-arrays storage for lit, tinted quads in camera-relative coordinates.
 * Every quad has one colour per vertex, so smooth lighting and ambient occlusion can vary across a face.
 */
public final class QuadBuffer {

    private float[] positions = new float[12 * 64];
    private float[] uvs = new float[8 * 64];
    private int[] colors = new int[4 * 64];
    private int[] textures = new int[64];
    private int count;

    /**
     * Appends a quad with the same colour on all four vertices.
     *
     * @param pos 12 floats, block-local vertex positions.
     * @param offsetX X offset added to every vertex.
     * @param offsetY Y offset added to every vertex.
     * @param offsetZ Z offset added to every vertex.
     * @param uv 8 floats of texture coordinates.
     * @param texture Texture id.
     * @param rgb Vertex colour multiplier as {@code 0xRRGGBB}.
     */
    public void add(float[] pos, float offsetX, float offsetY, float offsetZ, float[] uv, int texture, int rgb) {
        int slot = this.reserve();
        this.write(slot, pos, offsetX, offsetY, offsetZ, uv, texture, 0);
        Arrays.fill(this.colors, slot * 4, slot * 4 + 4, rgb);
    }

    /**
     * Appends a quad with one colour per vertex.
     *
     * @param pos 12 floats, block-local vertex positions.
     * @param offsetX X offset added to every vertex.
     * @param offsetY Y offset added to every vertex.
     * @param offsetZ Z offset added to every vertex.
     * @param uv 8 floats of texture coordinates.
     * @param texture Texture id.
     * @param rgb 4 vertex colour multipliers as {@code 0xRRGGBB}, in the order of {@code pos}.
     * @param rotate Number of vertices the quad is rotated by (0 or 1), which flips the diagonal it is split along.
     */
    public void add(float[] pos, float offsetX, float offsetY, float offsetZ, float[] uv, int texture, int[] rgb, int rotate) {
        int slot = this.reserve();
        this.write(slot, pos, offsetX, offsetY, offsetZ, uv, texture, rotate);
        for (int v = 0; v < 4; v++) {
            this.colors[slot * 4 + v] = rgb[(v + rotate) & 3];
        }
    }

    private int reserve() {
        if (this.count == this.textures.length) {
            int capacity = this.count * 2;
            this.positions = Arrays.copyOf(this.positions, capacity * 12);
            this.uvs = Arrays.copyOf(this.uvs, capacity * 8);
            this.colors = Arrays.copyOf(this.colors, capacity * 4);
            this.textures = Arrays.copyOf(this.textures, capacity);
        }
        return this.count++;
    }

    private void write(int slot, float[] pos, float offsetX, float offsetY, float offsetZ, float[] uv, int texture, int rotate) {
        int p = slot * 12;
        int t = slot * 8;
        for (int v = 0; v < 4; v++) {
            int s = (v + rotate) & 3;
            this.positions[p + v * 3] = pos[s * 3] + offsetX;
            this.positions[p + v * 3 + 1] = pos[s * 3 + 1] + offsetY;
            this.positions[p + v * 3 + 2] = pos[s * 3 + 2] + offsetZ;
            this.uvs[t + v * 2] = uv[s * 2];
            this.uvs[t + v * 2 + 1] = uv[s * 2 + 1];
        }
        this.textures[slot] = texture;
    }

    /** @return Number of quads. */
    public int count() { return this.count; }

    /** @return Vertex positions, 12 floats per quad. */
    public float[] positions() { return this.positions; }

    /** @return Texture coordinates, 8 floats per quad. */
    public float[] uvs() { return this.uvs; }

    /**
     * @param quad Quad index.
     * @param vertex Vertex index (0-3).
     * @return Colour multiplier as {@code 0xRRGGBB}.
     */
    public int color(int quad, int vertex) { return this.colors[quad * 4 + vertex]; }

    /**
     * @param quad Quad index.
     * @return Texture id.
     */
    public int texture(int quad) { return this.textures[quad]; }
}
