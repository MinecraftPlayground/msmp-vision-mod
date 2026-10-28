package dev.loat.msmp_vision.raster;

import dev.loat.msmp_vision.mesh.QuadBuffer;

/**
 * Transforms quads into clipped, back-face culled screen-space triangles.
 *
 * <p>Instances hold scratch buffers and must not be shared between threads.</p>
 */
public final class GeometryProjector {

    private static final float NEAR_W = 0.05f;
    private static final int V = TriangleBuffer.VERTEX;

    private final float[] m;
    private final int width;
    private final int height;
    private final boolean cull;
    private final float[][] quad = new float[4][V];
    private final float[][] input = new float[3][];
    private final float[][] clipped = new float[5][V];
    private final float[][] screen = new float[5][V];

    /**
     * Creates a projector.
     *
     * @param viewProjection Column-major view-projection matrix.
     * @param width Framebuffer width.
     * @param height Framebuffer height.
     * @param backfaceCulling Whether back faces are dropped.
     */
    public GeometryProjector(float[] viewProjection, int width, int height, boolean backfaceCulling) {
        this.m = viewProjection;
        this.width = width;
        this.height = height;
        this.cull = backfaceCulling;
    }

    /**
     * Projects all quads of a buffer.
     *
     * @param quads The quads.
     * @return Screen-space triangles.
     */
    public TriangleBuffer project(QuadBuffer quads) {
        TriangleBuffer out = new TriangleBuffer();
        for (int q = 0; q < quads.count(); q++) {
            if (!this.load(quads, q)) {
                continue;
            }
            int texture = quads.texture(q);
            this.emit(out, texture, this.quad[0], this.quad[1], this.quad[2]);
            this.emit(out, texture, this.quad[0], this.quad[2], this.quad[3]);
        }
        return out;
    }

    /** Loads a quad into clip space; returns {@code false} if it is trivially invisible. */
    private boolean load(QuadBuffer quads, int q) {
        float[] pos = quads.positions();
        float[] uv = quads.uvs();

        boolean left = true, right = true, below = true, above = true, behind = true;
        for (int v = 0; v < 4; v++) {
            int i = q * 12 + v * 3;
            float px = pos[i], py = pos[i + 1], pz = pos[i + 2];
            float x = this.m[0] * px + this.m[4] * py + this.m[8] * pz + this.m[12];
            float y = this.m[1] * px + this.m[5] * py + this.m[9] * pz + this.m[13];
            float w = this.m[3] * px + this.m[7] * py + this.m[11] * pz + this.m[15];
            float[] c = this.quad[v];
            c[0] = x;
            c[1] = y;
            c[2] = w;
            c[3] = uv[q * 8 + v * 2];
            c[4] = uv[q * 8 + v * 2 + 1];
            int color = quads.color(q, v);
            c[5] = ((color >> 16) & 0xFF) / 255f;
            c[6] = ((color >> 8) & 0xFF) / 255f;
            c[7] = (color & 0xFF) / 255f;
            if (x >= -w) left = false;
            if (x <= w) right = false;
            if (y >= -w) below = false;
            if (y <= w) above = false;
            if (w >= NEAR_W) behind = false;
        }
        return !(left || right || below || above || behind);
    }

    private void emit(TriangleBuffer out, int texture, float[] a, float[] b, float[] c) {
        this.input[0] = a;
        this.input[1] = b;
        this.input[2] = c;
        int n = this.clipNear();
        if (n < 3) {
            return;
        }
        for (int i = 0; i < n; i++) {
            float[] s = this.clipped[i];
            float[] d = this.screen[i];
            float invW = 1f / s[2];
            d[0] = (s[0] * invW * 0.5f + 0.5f) * this.width;
            d[1] = (0.5f - s[1] * invW * 0.5f) * this.height;
            d[2] = invW;
            d[3] = s[3] * invW;
            d[4] = s[4] * invW;
            d[5] = s[5] * invW;
            d[6] = s[6] * invW;
            d[7] = s[7] * invW;
        }
        for (int i = 1; i < n - 1; i++) {
            float[] p = this.screen[0];
            float[] q = this.screen[i];
            float[] r = this.screen[i + 1];
            double area = (double) (q[0] - p[0]) * (r[1] - p[1]) - (double) (q[1] - p[1]) * (r[0] - p[0]);
            if (area == 0 || (this.cull && area > 0)) {
                continue;
            }
            float depth = (1f / p[2] + 1f / q[2] + 1f / r[2]) / 3f;
            out.add(texture, p, q, r, depth);
        }
    }

    /** Sutherland-Hodgman clip of {@link #input} against {@code w >= NEAR_W} into {@link #clipped}. */
    private int clipNear() {
        int n = 0;
        for (int i = 0; i < 3; i++) {
            float[] current = this.input[i];
            float[] next = this.input[(i + 1) % 3];
            boolean currentIn = current[2] >= NEAR_W;
            boolean nextIn = next[2] >= NEAR_W;
            if (currentIn) {
                System.arraycopy(current, 0, this.clipped[n++], 0, V);
            }
            if (currentIn != nextIn) {
                float t = (NEAR_W - current[2]) / (next[2] - current[2]);
                float[] dest = this.clipped[n++];
                for (int k = 0; k < V; k++) {
                    dest[k] = current[k] + (next[k] - current[k]) * t;
                }
            }
        }
        return n;
    }
}
