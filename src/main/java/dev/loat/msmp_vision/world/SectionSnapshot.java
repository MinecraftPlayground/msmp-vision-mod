package dev.loat.msmp_vision.world;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.DataLayer;

/**
 * Immutable copy of one 16x16x16 chunk section (blocks, biomes, light), safe to read from any thread.
 */
public final class SectionSnapshot {

    private final int sectionX;
    private final int sectionY;
    private final int sectionZ;
    private final PalettedContainer<BlockState> states;
    private final PalettedContainerRO<Holder<Biome>> biomes;
    private final DataLayer skyLight;
    private final DataLayer blockLight;

    /**
     * Creates a snapshot.
     *
     * @param sectionX Section X.
     * @param sectionY Section Y.
     * @param sectionZ Section Z.
     * @param states Copied block container, or {@code null} for an all-air section.
     * @param biomes Copied biome container.
     * @param skyLight Copied sky light, or {@code null}.
     * @param blockLight Copied block light, or {@code null}.
     */
    public SectionSnapshot(
        int sectionX,
        int sectionY,
        int sectionZ,
        PalettedContainer<BlockState> states,
        PalettedContainerRO<Holder<Biome>> biomes,
        DataLayer skyLight,
        DataLayer blockLight
    ) {
        this.sectionX = sectionX;
        this.sectionY = sectionY;
        this.sectionZ = sectionZ;
        this.states = states;
        this.biomes = biomes;
        this.skyLight = skyLight;
        this.blockLight = blockLight;
    }

    /** @return Section X. */
    public int sectionX() { return this.sectionX; }

    /** @return Section Y. */
    public int sectionY() { return this.sectionY; }

    /** @return Section Z. */
    public int sectionZ() { return this.sectionZ; }

    /** @return {@code true} if the section contains only air. */
    public boolean isEmpty() { return this.states == null; }

    /**
     * Reads a block.
     *
     * @param x Local X (0-15).
     * @param y Local Y (0-15).
     * @param z Local Z (0-15).
     * @return The block state.
     */
    public BlockState block(int x, int y, int z) {
        return this.states == null ? Blocks.AIR.defaultBlockState() : this.states.get(x, y, z);
    }

    /**
     * Reads a biome (4x4x4 resolution).
     *
     * @param x Local X (0-15).
     * @param y Local Y (0-15).
     * @param z Local Z (0-15).
     * @return The biome holder.
     */
    public Holder<Biome> biome(int x, int y, int z) {
        return this.biomes.get(x >> 2, y >> 2, z >> 2);
    }

    /**
     * Reads sky light.
     *
     * @param x Local X.
     * @param y Local Y.
     * @param z Local Z.
     * @param fallback Value used when no light data exists.
     * @return Light level 0-15.
     */
    public int skyLight(int x, int y, int z, int fallback) {
        return this.skyLight == null ? fallback : this.skyLight.get(x, y, z);
    }

    /**
     * Reads block light.
     *
     * @param x Local X.
     * @param y Local Y.
     * @param z Local Z.
     * @return Light level 0-15.
     */
    public int blockLight(int x, int y, int z) {
        return this.blockLight == null ? 0 : this.blockLight.get(x, y, z);
    }
}
