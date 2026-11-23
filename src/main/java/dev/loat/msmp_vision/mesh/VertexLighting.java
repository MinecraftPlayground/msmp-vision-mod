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
    private final float gamma;
    private final int[] sideU = new int[3];
    private final int[] sideV = new int[3];
    private final int[] corner = new int[3];
    private final float[] scratch = new float[3];
    private final float blockAmbient;

    private float tintR = 1.0f;
    private float tintG = 0.85f;
    private float tintB = 0.55f;
    private float skyR = 1.0f;
    private float skyG = 1.0f;
    private float skyB = 1.0f;
    private float ambientR = 0.039f;
    private float ambientG = 0.039f;
    private float ambientB = 0.039f;

    /**
     * Creates a calculator.
     *
     * @param samples The world.
     * @param skyLightFactor Sky light multiplier, 0 (night) to 1 (day).
     * @param gamma Brightness option like in Minecraft's video settings: 0 = Moody, 0.5 = default, 1 = Bright.
     */
    public VertexLighting(Samples samples, float skyLightFactor, float gamma) {
        this(samples, skyLightFactor, gamma, 0f);
    }

    /**
     * Creates a calculator.
     *
     * @param samples The world.
     * @param skyLightFactor Sky light multiplier, 0 (night) to 1 (day).
     * @param gamma Brightness option like in Minecraft's video settings: 0 = Moody, 0.5 = default, 1 = Bright.
     * @param blockAmbient How far block light levels above 0 are lifted towards full brightness, 0..1 (empirical).
     */
    public VertexLighting(Samples samples, float skyLightFactor, float gamma, float blockAmbient) {
        this.samples = samples;
        this.skyLightFactor = skyLightFactor;
        this.gamma = Math.max(0f, Math.min(1f, gamma));
        this.blockAmbient = Math.max(0f, Math.min(1f, blockAmbient));
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
     * @param out Receives 4 colours as 12 floats (r, g, b per vertex, 0..1), without biome tint.
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
            this.color(sky, block, this.scratch, 0);
            float factor = directionalShade * shade;
            out[i * 3] = factor * this.scratch[0];
            out[i * 3 + 1] = factor * this.scratch[1];
            out[i * 3 + 2] = factor * this.scratch[2];
        }
        float v0 = out[0] + out[1] + out[2];
        float v1 = out[3] + out[4] + out[5];
        float v2 = out[6] + out[7] + out[8];
        float v3 = out[9] + out[10] + out[11];
        return v0 + v2 > v1 + v3 ? 1 : 0;
    }

    /**
     * Colour of the light, like Minecraft's lightmap: block light times its tint plus sky light times its colour plus the
     * ambient colour, then the brightness option blends the result towards {@code 1 - (1 - x)^4}.
     *
     * @param sky Sky light 0-15.
     * @param block Block light 0-15.
     * @param out Receives r, g, b (0..1) at {@code offset}.
     * @param offset First index in {@code out}.
     */
    public void color(float sky, float block, float[] out, int offset) {
        float curved = curve(block / 15f);
        // The lift was measured at levels 5-8. Below that, client screenshots of dark caves show no lift, so it fades in
        // (quadratically) between level 1 (none) and level 5 (full).
        float ramp = Math.max(0f, Math.min(1f, (block - 1f) / 4f));
        float lift = ramp * ramp;
        float b = curved + (1f - curved) * this.blockAmbient * lift;
        float s = curve(sky / 15f) * this.skyLightFactor;
        out[offset] = this.finish(b * this.tintR + s * this.skyR + this.ambientR);
        out[offset + 1] = this.finish(b * this.tintG + s * this.skyG + this.ambientG);
        out[offset + 2] = this.finish(b * this.tintB + s * this.skyB + this.ambientB);
    }

    /**
     * Sets the light colours of the environment (the {@code BLOCK_LIGHT_TINT}, {@code SKY_LIGHT_COLOR} and
     * {@code AMBIENT_LIGHT_COLOR} attributes).
     *
     * @param blockTint Tint of block light as {@code 0xRRGGBB}.
     * @param skyColor Colour of sky light as {@code 0xRRGGBB}.
     * @param ambient Ambient light added everywhere as {@code 0xRRGGBB}.
     * @return this
     */
    public VertexLighting colors(int blockTint, int skyColor, int ambient) {
        this.tintR = ((blockTint >> 16) & 0xFF) / 255f;
        this.tintG = ((blockTint >> 8) & 0xFF) / 255f;
        this.tintB = (blockTint & 0xFF) / 255f;
        this.skyR = ((skyColor >> 16) & 0xFF) / 255f;
        this.skyG = ((skyColor >> 8) & 0xFF) / 255f;
        this.skyB = (skyColor & 0xFF) / 255f;
        this.ambientR = ((ambient >> 16) & 0xFF) / 255f;
        this.ambientG = ((ambient >> 8) & 0xFF) / 255f;
        this.ambientB = (ambient & 0xFF) / 255f;
        return this;
    }

    /**
     * Grey brightness of a light level (the mean of {@link #color}), for callers that need a single value.
     *
     * @param sky Sky light 0-15.
     * @param block Block light 0-15.
     * @return Brightness 0..1.
     */
    public float brightness(float sky, float block) {
        this.color(sky, block, this.scratch, 0);
        return (this.scratch[0] + this.scratch[1] + this.scratch[2]) / 3f;
    }

    private static float curve(float level) {
        return level / (4f - 3f * level);
    }

    private float finish(float value) {
        float clamped = Math.max(0f, Math.min(1f, value));
        float inverse = 1f - clamped;
        float bright = 1f - inverse * inverse * inverse * inverse;
        return clamped + (bright - clamped) * this.gamma;
    }

    private float shade(int[] p) {
        return this.samples.occluder(p[0], p[1], p[2]) ? OCCLUDED_SHADE : 1f;
    }
}
