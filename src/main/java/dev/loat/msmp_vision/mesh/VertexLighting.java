package dev.loat.msmp_vision.mesh;

/**
 * Smooth lighting and ambient occlusion of a quad, following Minecraft's "smooth lighting" algorithm.
 *
 * <p>For every vertex of a face, the four blocks around that vertex in the plane of the face are sampled
 * (the block in front of the face and its two edge neighbours plus the diagonal one). Their light is averaged,
 * and their ambient occlusion shade (0.2 for blocks that fill the whole cube, 1.0 otherwise) is averaged as well.
 * If both edge neighbours are opaque, the diagonal block is hidden and is replaced by an edge neighbour, which
 * prevents light from leaking through corners. Samples inside opaque blocks use the light of the centre sample
 * instead of their own (zero) light, so only the occlusion shade darkens a corner.</p>
 *
 * <p>This class does not depend on Minecraft types. Instances hold scratch buffers and must not be shared
 * between threads.</p>
 */
public final class VertexLighting {

    /** Shade of a block that fills its whole cube, compared to 1.0 for everything else. */
    public static final float OCCLUDED_SHADE = 0.2f;

    /** Access to the surrounding world. */
    public interface Samples {

        /**
         * @param x World X.
         * @param y World Y.
         * @param z World Z.
         * @return {@code true} if the block hides what is behind it (used for corner handling and light sampling).
         */
        boolean opaque(int x, int y, int z);

        /**
         * @param x World X.
         * @param y World Y.
         * @param z World Z.
         * @return {@code true} if the block darkens neighbouring faces (ambient occlusion).
         */
        boolean occluder(int x, int y, int z);

        /**
         * @param x World X.
         * @param y World Y.
         * @param z World Z.
         * @return Sky light 0-15.
         */
        int sky(int x, int y, int z);

        /**
         * @param x World X.
         * @param y World Y.
         * @param z World Z.
         * @return Block light 0-15.
         */
        int block(int x, int y, int z);
    }

    private final Samples samples;
    private final float skyLightFactor;
    private final float minLight;
    private final int[] sideU = new int[3];
    private final int[] sideV = new int[3];
    private final int[] corner = new int[3];

    /**
     * Creates a calculator.
     *
     * @param samples The world.
     * @param skyLightFactor Sky light multiplier, 0 (night) to 1 (day).
     * @param minLight Brightness floor, so that fully dark places do not become pitch black.
     */
    public VertexLighting(Samples samples, float skyLightFactor, float minLight) {
        this.samples = samples;
        this.skyLightFactor = skyLightFactor;
        this.minLight = minLight;
    }

    /**
     * Tells whether a quad lies in the plane of the block face in direction {@code axis}/{@code sign}.
     * Only such quads are lit from the neighbouring block; others (plants, slabs) use flat lighting.
     *
     * @param pos 4 vertices x 3 floats, block-local.
     * @param axis Axis of the face normal (0 = X, 1 = Y, 2 = Z).
     * @param sign Direction along the axis (+1 or -1).
     * @return {@code true} if all four vertices lie on that face of the block.
     */
    public static boolean isFullFace(float[] pos, int axis, int sign) {
        float expected = sign > 0 ? 1f : 0f;
        for (int v = 0; v < 4; v++) {
            if (Math.abs(pos[v * 3 + axis] - expected) > 1e-3f) {
                return false;
            }
        }
        return true;
    }

    /**
     * Computes the brightness of all four vertices of a face.
     *
     * @param x Block X.
     * @param y Block Y.
     * @param z Block Z.
     * @param axis Axis of the face normal (0 = X, 1 = Y, 2 = Z).
     * @param sign Direction along the axis (+1 or -1).
     * @param pos 4 vertices x 3 floats, block-local.
     * @param directionalShade Constant per-face shade (1.0 up, 0.5 down, ...), 1.0 if the quad is unshaded.
     * @param out Receives 4 brightness values (0..1), without biome tint.
     * @return {@code 1} if the quad should be rotated by one vertex so that its diagonal follows the darker
     * vertex pair (avoids visible artefacts), otherwise {@code 0}.
     */
    public int compute(int x, int y, int z, int axis, int sign, float[] pos, float directionalShade, float[] out) {
        int bx = axis == 0 ? x + sign : x;
        int by = axis == 1 ? y + sign : y;
        int bz = axis == 2 ? z + sign : z;
        int u = axis == 0 ? 1 : 0;
        int v = axis == 2 ? 1 : 2;

        float centerShade = this.samples.occluder(bx, by, bz) ? OCCLUDED_SHADE : 1f;
        int centerSky = this.samples.sky(bx, by, bz);
        int centerBlock = this.samples.block(bx, by, bz);

        for (int i = 0; i < 4; i++) {
            int su = pos[i * 3 + u] >= 0.5f ? 1 : -1;
            int sv = pos[i * 3 + v] >= 0.5f ? 1 : -1;

            this.sideU[0] = bx; this.sideU[1] = by; this.sideU[2] = bz;
            this.sideU[u] += su;
            this.sideV[0] = bx; this.sideV[1] = by; this.sideV[2] = bz;
            this.sideV[v] += sv;
            this.corner[0] = this.sideU[0]; this.corner[1] = this.sideU[1]; this.corner[2] = this.sideU[2];
            this.corner[v] += sv;

            boolean opaqueU = this.samples.opaque(this.sideU[0], this.sideU[1], this.sideU[2]);
            boolean opaqueV = this.samples.opaque(this.sideV[0], this.sideV[1], this.sideV[2]);

            float shadeU = this.shade(this.sideU);
            float shadeV = this.shade(this.sideV);
            float skyU = opaqueU ? centerSky : this.samples.sky(this.sideU[0], this.sideU[1], this.sideU[2]);
            float skyV = opaqueV ? centerSky : this.samples.sky(this.sideV[0], this.sideV[1], this.sideV[2]);
            float blockU = opaqueU ? centerBlock : this.samples.block(this.sideU[0], this.sideU[1], this.sideU[2]);
            float blockV = opaqueV ? centerBlock : this.samples.block(this.sideV[0], this.sideV[1], this.sideV[2]);

            float shadeC;
            float skyC;
            float blockC;
            if (opaqueU && opaqueV) {
                shadeC = shadeU;
                skyC = skyU;
                blockC = blockU;
            } else {
                boolean opaqueC = this.samples.opaque(this.corner[0], this.corner[1], this.corner[2]);
                shadeC = this.shade(this.corner);
                skyC = opaqueC ? centerSky : this.samples.sky(this.corner[0], this.corner[1], this.corner[2]);
                blockC = opaqueC ? centerBlock : this.samples.block(this.corner[0], this.corner[1], this.corner[2]);
            }

            float shade = (centerShade + shadeU + shadeV + shadeC) * 0.25f;
            float sky = (centerSky + skyU + skyV + skyC) * 0.25f;
            float block = (centerBlock + blockU + blockV + blockC) * 0.25f;
            out[i] = directionalShade * shade * this.brightness(sky, block);
        }
        return out[0] + out[2] > out[1] + out[3] ? 1 : 0;
    }

    /**
     * Brightness for a light level, using Minecraft's light curve.
     *
     * @param sky Sky light 0-15.
     * @param block Block light 0-15.
     * @return Brightness 0..1, at least the configured floor.
     */
    public float brightness(float sky, float block) {
        float level = Math.max(sky / 15f * this.skyLightFactor, block / 15f);
        return Math.max(level / (4f - 3f * level), this.minLight);
    }

    private float shade(int[] p) {
        return this.samples.occluder(p[0], p[1], p[2]) ? OCCLUDED_SHADE : 1f;
    }
}
