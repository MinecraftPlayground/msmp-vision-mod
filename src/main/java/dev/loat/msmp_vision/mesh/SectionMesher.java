package dev.loat.msmp_vision.mesh;

import dev.loat.msmp_vision.assets.TextureData;
import dev.loat.msmp_vision.assets.TextureStore;
import dev.loat.msmp_vision.model.BakedQuad;
import dev.loat.msmp_vision.model.BlockModelBaker;
import dev.loat.msmp_vision.model.FaceGeometry;
import dev.loat.msmp_vision.render.Camera;
import dev.loat.msmp_vision.world.EnvironmentSnapshot;
import dev.loat.msmp_vision.world.SectionSnapshot;
import dev.loat.msmp_vision.world.WorldSnapshot;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Builds the {@link SectionGeometry} of one section: block quads with flat lighting, face shading and biome tint,
 * plus simple fluid surfaces. Vertex colours are fully precomputed so the fragment stage only multiplies.
 *
 * <p>Instances hold scratch buffers and must not be shared between threads.</p>
 */
public final class SectionMesher {

    /** Directional shading per {@link Direction#ordinal()}: DOWN, UP, NORTH, SOUTH, WEST, EAST. */
    private static final float[] FACE_SHADE = {0.5f, 1.0f, 0.8f, 0.8f, 0.6f, 0.6f};
    private static final float MIN_LIGHT = 0.04f;
    /**
     * Debug aid, enabled with the JVM flag {@code -Dmsmp_vision.debug.sections=true}: tints chunk columns in an
     * alternating red/blue checkerboard so that chunk borders and missing columns become visible in the screenshot.
     */
    private static final boolean DEBUG_SECTIONS = Boolean.getBoolean("msmp_vision.debug.sections");
    private static final float[] FLUID_UV_TOP = {0, 0, 0, 1, 1, 1, 1, 0};

    private final WorldSnapshot world;
    private final EnvironmentSnapshot environment;
    private final BlockModelBaker baker;
    private final TextureStore textures;
    private final TintResolver tint;
    private final int originX;
    private final int originY;
    private final int originZ;
    private final int blend;
    private final float[] scratch = new float[12];
    private final float[] sideUv = new float[8];
    private int debugTint = 0xFFFFFF;

    /**
     * Creates a mesher.
     *
     * @param world The captured world.
     * @param baker Block model baker.
     * @param textures Texture registry.
     * @param tint Tint resolver.
     * @param camera Camera providing the coordinate origin.
     * @param biomeBlend Biome blend radius.
     */
    public SectionMesher(WorldSnapshot world, BlockModelBaker baker, TextureStore textures, TintResolver tint, Camera camera, int biomeBlend) {
        this.world = world;
        this.environment = world.environment();
        this.baker = baker;
        this.textures = textures;
        this.tint = tint;
        this.originX = camera.originX();
        this.originY = camera.originY();
        this.originZ = camera.originZ();
        this.blend = biomeBlend;
    }

    /**
     * Meshes a section.
     *
     * @param section The section.
     * @return The geometry, possibly empty.
     */
    public SectionGeometry mesh(SectionSnapshot section) {
        QuadBuffer opaque = new QuadBuffer();
        QuadBuffer translucent = new QuadBuffer();
        if (DEBUG_SECTIONS) {
            this.debugTint = ((section.sectionX() + section.sectionZ()) & 1) == 0 ? 0xFFA0A0 : 0xA0A0FF;
        }
        int baseX = section.sectionX() << 4;
        int baseY = section.sectionY() << 4;
        int baseZ = section.sectionZ() << 4;

        for (int ly = 0; ly < 16; ly++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    BlockState state = section.block(lx, ly, lz);
                    if (state.isAir()) {
                        continue;
                    }
                    int x = baseX + lx;
                    int y = baseY + ly;
                    int z = baseZ + lz;
                    boolean solid = state.isSolidRender();
                    if (solid && this.enclosed(x, y, z)) {
                        continue;
                    }
                    FluidState fluid = state.getFluidState();
                    if (!fluid.isEmpty()) {
                        this.meshFluid(fluid, x, y, z, opaque, translucent);
                    }
                    this.meshBlock(state, solid, x, y, z, opaque, translucent);
                }
            }
        }
        return new SectionGeometry(opaque, translucent);
    }

    private boolean enclosed(int x, int y, int z) {
        for (Direction direction : Direction.values()) {
            if (!this.world.blockState(x + direction.getStepX(), y + direction.getStepY(), z + direction.getStepZ()).isSolidRender()) {
                return false;
            }
        }
        return true;
    }

    private void meshBlock(BlockState state, boolean solid, int x, int y, int z, QuadBuffer opaque, QuadBuffer translucent) {
        for (BakedQuad quad : this.baker.bake(state)) {
            Direction cull = quad.cullFace();
            if (cull != null && this.world.blockState(x + cull.getStepX(), y + cull.getStepY(), z + cull.getStepZ()).isSolidRender()) {
                continue;
            }
            Direction face = quad.face();
            float light = this.light(x, y, z, face, solid);
            float shade = quad.shade() ? FACE_SHADE[face.ordinal()] : 1.0f;
            int tintRgb = multiply(quad.tintIndex() >= 0 ? this.tint.blockTint(this.world, state, x, y, z, this.blend) : 0xFFFFFF, this.debugTint);
            int color = pack(shade * light, tintRgb);
            (quad.translucent() ? translucent : opaque).add(quad.positions(), x - this.originX, y - this.originY, z - this.originZ, quad.uvs(), quad.texture(), color);
        }
    }

    private void meshFluid(FluidState fluid, int x, int y, int z, QuadBuffer opaque, QuadBuffer translucent) {
        boolean water = fluid.is(FluidTags.WATER);
        TextureData still = this.textures.get(water ? "minecraft:block/water_still" : "minecraft:block/lava_still");
        TextureData flow = this.textures.get(water ? "minecraft:block/water_flow" : "minecraft:block/lava_flow");
        QuadBuffer target = water ? translucent : opaque;
        int tintRgb = multiply(water ? this.tint.waterTint(this.world, x, y, z, this.blend) : 0xFFFFFF, this.debugTint);
        float light = water ? this.light(x, y, z, Direction.UP, false) : 1.0f;

        FluidState above = this.world.blockState(x, y + 1, z).getFluidState();
        boolean covered = !above.isEmpty() && above.getType().isSame(fluid.getType());
        float height = covered ? 1.0f : fluid.getOwnHeight();
        float rx = x - this.originX;
        float ry = y - this.originY;
        float rz = z - this.originZ;

        if (!covered) {
            FaceGeometry.fill(this.scratch, Direction.UP, 0, 0, 0, 1, height, 1);
            target.add(this.scratch, rx, ry, rz, FLUID_UV_TOP, still.id(), pack(light * FACE_SHADE[Direction.UP.ordinal()], tintRgb));
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockState neighbour = this.world.blockState(x + side.getStepX(), y, z + side.getStepZ());
            FluidState neighbourFluid = neighbour.getFluidState();
            if ((!neighbourFluid.isEmpty() && neighbourFluid.getType().isSame(fluid.getType())) || neighbour.isSolidRender()) {
                continue;
            }
            float v0 = (1.0f - height) * 0.5f;
            this.sideUv[0] = 0; this.sideUv[1] = v0;
            this.sideUv[2] = 0; this.sideUv[3] = 0.5f;
            this.sideUv[4] = 0.5f; this.sideUv[5] = 0.5f;
            this.sideUv[6] = 0.5f; this.sideUv[7] = v0;
            FaceGeometry.fill(this.scratch, side, 0, 0, 0, 1, height, 1);
            float sideLight = water ? this.light(x + side.getStepX(), y, z + side.getStepZ(), side, false) : 1.0f;
            target.add(this.scratch, rx, ry, rz, this.sideUv, flow.id(), pack(sideLight * FACE_SHADE[side.ordinal()], tintRgb));
        }
    }

    /** Light of a face: sampled at the neighbour for solid blocks, at the block itself otherwise. */
    private float light(int x, int y, int z, Direction face, boolean solid) {
        int lx = solid ? x + face.getStepX() : x;
        int ly = solid ? y + face.getStepY() : y;
        int lz = solid ? z + face.getStepZ() : z;
        float sky = this.world.skyLight(lx, ly, lz) / 15.0f * this.environment.skyLightFactor();
        float block = this.world.blockLight(lx, ly, lz) / 15.0f;
        float level = Math.max(sky, block);
        return Math.max(level / (4.0f - 3.0f * level), MIN_LIGHT);
    }

    private static int multiply(int a, int b) {
        int r = ((a >> 16) & 0xFF) * ((b >> 16) & 0xFF) / 255;
        int g = ((a >> 8) & 0xFF) * ((b >> 8) & 0xFF) / 255;
        int bl = (a & 0xFF) * (b & 0xFF) / 255;
        return (r << 16) | (g << 8) | bl;
    }

    private static int pack(float brightness, int tintRgb) {
        int r = clamp(brightness * ((tintRgb >> 16) & 0xFF));
        int g = clamp(brightness * ((tintRgb >> 8) & 0xFF));
        int b = clamp(brightness * (tintRgb & 0xFF));
        return (r << 16) | (g << 8) | b;
    }

    private static int clamp(float value) {
        return Math.max(0, Math.min(255, (int) value));
    }
}
