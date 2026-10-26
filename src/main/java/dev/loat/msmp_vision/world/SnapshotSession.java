package dev.loat.msmp_vision.world;

import dev.loat.msmp_vision.config.files.chunks.ChunksConfig;
import dev.loat.msmp_vision.logging.Logger;
import dev.loat.msmp_vision.render.Camera;
import dev.loat.msmp_vision.render.RenderSettings;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Copies the chunk sections around the camera into a {@link WorldSnapshot}, in slices of limited duration.
 * Every method must be called on the server thread.
 *
 * <p>Chunks that are not loaded are loaded on demand (see {@link ChunksConfig}). Each chunk is copied into
 * the snapshot right after it became available, so it does not have to stay loaded until the render starts.
 * Chunks are processed nearest first, so a limit or timeout only costs the far end of the picture.</p>
 *
 * <p>{@link #step(long)} does a bounded amount of work and can be called once per server tick, which keeps the
 * server responsive while a large area is loaded. Calling it with {@link Long#MAX_VALUE} does everything at once.</p>
 */
public final class SnapshotSession {

    private static final int FALLBACK_FOG = 0xC0D8FF;
    private static final int FALLBACK_SKY = 0x78A7FF;

    /** A chunk column that may be visible, with its squared horizontal distance to the camera. */
    private record Column(int x, int z, double distanceSquared) {}

    private final ServerLevel level;
    private final Camera camera;
    private final RenderSettings settings;
    private final ChunksConfig chunks;
    private final WorldSnapshot snapshot;
    private final EnvironmentSnapshot environment;
    private final List<Column> columns;
    private final double radius;
    private final int minSectionY;
    private final long loadLimitNanos;

    private int next;
    private int resident;
    private int loaded;
    private int skipped;
    private long workNanos;
    private boolean finished;

    /**
     * Prepares a capture: reads the environment and determines which chunk columns are in view.
     *
     * @param level The level.
     * @param camera The camera used for frustum culling.
     * @param settings The render settings.
     * @param chunks The chunk loading configuration.
     */
    public SnapshotSession(ServerLevel level, Camera camera, RenderSettings settings, ChunksConfig chunks) {
        this.level = level;
        this.camera = camera;
        this.settings = settings;
        this.chunks = chunks;

        Vec3 position = new Vec3(settings.position().get(0), settings.position().get(1), settings.position().get(2));
        this.environment = new EnvironmentSnapshot(
            level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, position) * Mth.DEG_TO_RAD,
            level.environmentAttributes().getValue(EnvironmentAttributes.SKY_LIGHT_FACTOR, position),
            toRgb(level.environmentAttributes().getValue(EnvironmentAttributes.FOG_COLOR, position), FALLBACK_FOG),
            toRgb(level.environmentAttributes().getValue(EnvironmentAttributes.SKY_COLOR, position), FALLBACK_SKY),
            level.dimensionType().hasSkyLight()
        );
        this.snapshot = new WorldSnapshot(this.environment);
        this.radius = settings.renderDistanceBlocks() + 16;
        this.minSectionY = level.getMinSectionY();
        this.loadLimitNanos = TimeUnit.SECONDS.toNanos(Math.max(0, chunks.loadTimeoutSeconds));
        this.columns = visibleColumns(level, camera, settings, this.radius);
    }

    /**
     * Processes chunk columns until the budget is used up (at least one column per call).
     *
     * @param budgetNanos Maximum time for this call, {@link Long#MAX_VALUE} for no limit.
     * @return {@code true} once the capture is complete.
     */
    public boolean step(long budgetNanos) {
        if (this.finished) {
            return true;
        }
        ServerChunkCache cache = this.level.getChunkSource();
        LevelLightEngine light = this.level.getLightEngine();
        long stepStart = System.nanoTime();
        int processed = 0;

        while (this.next < this.columns.size()) {
            if (processed > 0 && budgetNanos != Long.MAX_VALUE && System.nanoTime() - stepStart >= budgetNanos) {
                break;
            }
            Column column = this.columns.get(this.next++);
            processed++;

            LevelChunk chunk = cache.getChunkNow(column.x(), column.z());
            if (chunk != null) {
                this.resident++;
            } else if (this.chunks.mode == ChunksConfig.Mode.LOAD) {
                long spent = this.workNanos + (System.nanoTime() - stepStart);
                if (this.loaded >= this.chunks.maxChunkLoads || spent >= this.loadLimitNanos) {
                    this.skipped++;
                    continue;
                }
                ChunkAccess access = cache.getChunk(column.x(), column.z(), ChunkStatus.FULL, true);
                this.loaded++;
                if (access instanceof LevelChunk levelChunk) {
                    chunk = levelChunk;
                }
            }
            if (chunk != null) {
                this.copyColumn(chunk, column, light);
            }
        }
        this.workNanos += System.nanoTime() - stepStart;

        if (this.next < this.columns.size()) {
            return false;
        }
        this.finished = true;
        Logger.info("Captured %d section(s) from %d chunk column(s) in view at render distance %d chunks (%d already loaded, %d newly loaded, %d skipped) in %d ms."
            .formatted(this.snapshot.sections().size(), this.columns.size(), this.settings.renderDistance(), this.resident, this.loaded, this.skipped, this.workNanos / 1_000_000L));
        if (this.skipped > 0) {
            Logger.warning("Chunk loading limit reached: %d chunk(s) were not loaded and are missing in the screenshot. Raise 'chunks.max-chunk-loads' or 'chunks.load-timeout-seconds', or lower the render distance."
                .formatted(this.skipped));
        }
        return true;
    }

    /** @return The snapshot; only complete once {@link #step(long)} returned {@code true}. */
    public WorldSnapshot snapshot() { return this.snapshot; }

    /** @return Total time in nanoseconds spent inside {@link #step(long)}, i.e. blocked on the server thread. */
    public long workNanos() { return this.workNanos; }

    /** All chunk columns inside the render radius and the view frustum, nearest first. */
    private static List<Column> visibleColumns(ServerLevel level, Camera camera, RenderSettings settings, double radius) {
        double px = settings.position().get(0);
        double pz = settings.position().get(2);
        int minChunkX = Mth.floor(px - radius) >> 4;
        int maxChunkX = Mth.floor(px + radius) >> 4;
        int minChunkZ = Mth.floor(pz - radius) >> 4;
        int maxChunkZ = Mth.floor(pz + radius) >> 4;
        // Only sections within the render radius are ever used, so the column test only needs that height range.
        // Testing the full world height would let the tilted frustum planes pick up many columns that can never be seen.
        double py = settings.position().get(1);
        int minY = Math.max(level.getMinY(), Mth.floor(py - radius));
        int maxY = Math.min(level.getMinY() + level.getHeight(), Mth.ceil(py + radius));
        double reach = radius + 12; // half diagonal of a chunk

        List<Column> columns = new ArrayList<>();
        for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                double dx = (cx << 4) + 8 - px;
                double dz = (cz << 4) + 8 - pz;
                double distanceSquared = dx * dx + dz * dz;
                if (distanceSquared > reach * reach || !camera.intersectsColumn(cx, cz, minY, maxY)) {
                    continue;
                }
                columns.add(new Column(cx, cz, distanceSquared));
            }
        }
        columns.sort(Comparator.comparingDouble(Column::distanceSquared));
        return columns;
    }

    private void copyColumn(LevelChunk chunk, Column column, LevelLightEngine light) {
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            int sy = this.minSectionY + i;
            if (!this.isRelevant(column.x(), sy, column.z())) {
                continue;
            }
            LevelChunkSection section = sections[i];
            SectionPos pos = SectionPos.of(column.x(), sy, column.z());
            this.snapshot.put(new SectionSnapshot(
                column.x(), sy, column.z(),
                section.hasOnlyAir() ? null : section.getStates().copy(),
                section.getBiomes().copy(),
                this.environment.hasSkyLight() ? copyLayer(light, LightLayer.SKY, pos) : null,
                copyLayer(light, LightLayer.BLOCK, pos)
            ));
        }
    }

    private boolean isRelevant(int cx, int sy, int cz) {
        double dx = (cx << 4) + 8 - this.settings.position().get(0);
        double dy = (sy << 4) + 8 - this.settings.position().get(1);
        double dz = (cz << 4) + 8 - this.settings.position().get(2);
        return dx * dx + dy * dy + dz * dz <= this.radius * this.radius && this.camera.intersectsSection(cx, sy, cz);
    }

    /**
     * Converts an environment attribute colour to {@code 0xRRGGBB}. Accepts packed integer colours (alpha is ignored)
     * as well as RGB vectors (components 0..1) and clamps everything so nothing can overflow into a neighbouring channel.
     */
    private static int toRgb(Object raw, int fallback) {
        if (raw instanceof Integer packed) {
            return packed & 0xFFFFFF;
        }
        if (raw instanceof Vector3fc vector) {
            return (channel(vector.x()) << 16) | (channel(vector.y()) << 8) | channel(vector.z());
        }
        return fallback;
    }

    private static int channel(float unit) {
        return Mth.clamp((int) (unit * 255.0f + 0.5f), 0, 255);
    }

    private static DataLayer copyLayer(LevelLightEngine engine, LightLayer layer, SectionPos pos) {
        DataLayer data = engine.getLayerListener(layer).getDataLayerData(pos);
        return data == null ? null : data.copy();
    }
}
