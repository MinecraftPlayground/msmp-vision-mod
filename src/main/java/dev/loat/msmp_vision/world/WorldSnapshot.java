package dev.loat.msmp_vision.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;

/**
 * A set of {@link SectionSnapshot}s plus environment data. Built on the server thread, afterwards read-only.
 */
public final class WorldSnapshot {

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final EnvironmentSnapshot environment;
    private final Long2ObjectOpenHashMap<SectionSnapshot> sections = new Long2ObjectOpenHashMap<>();

    /**
     * Creates an empty snapshot.
     *
     * @param environment Environment values.
     */
    public WorldSnapshot(EnvironmentSnapshot environment) {
        this.environment = environment;
    }

    /**
     * Adds a section. Only call while building.
     *
     * @param section The section.
     */
    public void put(SectionSnapshot section) {
        this.sections.put(SectionPos.asLong(section.sectionX(), section.sectionY(), section.sectionZ()), section);
    }

    /** @return Environment values. */
    public EnvironmentSnapshot environment() { return this.environment; }

    /** @return All captured sections. */
    public Collection<SectionSnapshot> sections() { return this.sections.values(); }

    /**
     * Reads a block in world coordinates.
     *
     * @param x World X.
     * @param y World Y.
     * @param z World Z.
     * @return The state, air if the position was not captured.
     */
    public BlockState blockState(int x, int y, int z) {
        SectionSnapshot s = this.section(x, y, z);
        return s == null ? AIR : s.block(x & 15, y & 15, z & 15);
    }

    /**
     * Reads sky light in world coordinates.
     *
     * @param x World X.
     * @param y World Y.
     * @param z World Z.
     * @return Light 0-15.
     */
    public int skyLight(int x, int y, int z) {
        if (!this.environment.hasSkyLight()) {
            return 0;
        }
        SectionSnapshot s = this.section(x, y, z);
        return s == null ? 15 : s.skyLight(x & 15, y & 15, z & 15, 15);
    }

    /**
     * Reads block light in world coordinates.
     *
     * @param x World X.
     * @param y World Y.
     * @param z World Z.
     * @return Light 0-15.
     */
    public int blockLight(int x, int y, int z) {
        SectionSnapshot s = this.section(x, y, z);
        return s == null ? 0 : s.blockLight(x & 15, y & 15, z & 15);
    }

    /**
     * Reads a biome in world coordinates.
     *
     * @param x World X.
     * @param y World Y.
     * @param z World Z.
     * @return The biome, or {@code null} if the position was not captured.
     */
    public Holder<Biome> biome(int x, int y, int z) {
        SectionSnapshot s = this.section(x, y, z);
        return s == null ? null : s.biome(x & 15, y & 15, z & 15);
    }

    private SectionSnapshot section(int x, int y, int z) {
        return this.sections.get(SectionPos.asLong(x >> 4, y >> 4, z >> 4));
    }
}
