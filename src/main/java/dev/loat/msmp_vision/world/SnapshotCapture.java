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
 * Copies the chunk sections around the camera into a {@link WorldSnapshot}. Must run on the server thread.
 *
 * <p>Chunks that are not loaded are loaded on demand (see {@link ChunksConfig}). Each chunk is copied into
 * the snapshot right after it became available, so it does not have to stay loaded until the render starts.
 * Chunks are processed nearest first, so a limit or timeout only costs the far end of the picture.</p>
 */
public final class SnapshotCapture {

    private static final int FALLBACK_FOG = 0xC0D8FF;
    private static final int FALLBACK_SKY = 0x78A7FF;

    /** A chunk column that may be visible, with its squared horizontal distance to the camera. */
    private record Column(int x, int z, double distanceSquared) {}

    private SnapshotCapture() {}

    /**
     * Captures all potentially visible sections, loading missing chunks if configured.
     *
     * @param level The level.
     * @param camera The camera used for frustum culling.
     * @param settings The render settings.
     * @param chunks The chunk loading configuration.
     * @return The snapshot.
     */
    public static WorldSnapshot capture(ServerLevel level, Camera camera, RenderSettings settings, ChunksConfig chunks) {
        Vec3 position = new Vec3(settings.position().get(0), settings.position().get(1), settings.position().get(2));
        EnvironmentSnapshot environment = new EnvironmentSnapshot(
            level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, position) * Mth.DEG_TO_RAD,
            level.environmentAttributes().getValue(EnvironmentAttributes.SKY_LIGHT_FACTOR, position),
            toRgb(level.environmentAttributes().getValue(EnvironmentAttributes.FOG_COLOR, position), FALLBACK_FOG),
            toRgb(level.environmentAttributes().getValue(EnvironmentAttributes.SKY_COLOR, position), FALLBACK_SKY),
            level.dimensionType().hasSkyLight()
        );
        WorldSnapshot snapshot = new WorldSnapshot(environment);

        double radius = settings.renderDistance() + 16;
        ServerChunkCache cache = level.getChunkSource();
        LevelLightEngine light = level.getLightEngine();
        int minSectionY = level.getMinSectionY();

        long start = System.nanoTime();
        long deadline = start + TimeUnit.SECONDS.toNanos(Math.max(0, chunks.loadTimeoutSeconds));
        List<Column> columns = visibleColumns(level, camera, settings, radius);
        int resident = 0;
        int loaded = 0;
        int skipped = 0;

        for (Column column : columns) {
            LevelChunk chunk = cache.getChunkNow(column.x(), column.z());
            if (chunk != null) {
                resident++;
            } else if (chunks.mode == ChunksConfig.Mode.LOAD) {
                if (loaded >= chunks.maxChunkLoads || System.nanoTime() >= deadline) {
                    skipped++;
                    continue;
                }
                ChunkAccess access = cache.getChunk(column.x(), column.z(), ChunkStatus.FULL, true);
                loaded++;
                if (access instanceof LevelChunk levelChunk) {
                    chunk = levelChunk;
                }
            }
            if (chunk != null) {
                copyColumn(snapshot, chunk, column, camera, settings, radius, light, minSectionY, environment.hasSkyLight());
            }
        }

        Logger.info("Captured %d section(s) from %d chunk column(s) in view (%d already loaded, %d newly loaded, %d skipped) in %d ms."
            .formatted(snapshot.sections().size(), columns.size(), resident, loaded, skipped, (System.nanoTime() - start) / 1_000_000L));
        if (skipped > 0) {
            Logger.warning("Chunk loading limit reached: %d chunk(s) were not loaded and are missing in the screenshot. Raise 'chunks.max-chunk-loads' or 'chunks.load-timeout-seconds', or lower the render distance."
                .formatted(skipped));
        }
        return snapshot;
    }

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

    private static void copyColumn(WorldSnapshot snapshot, LevelChunk chunk, Column column, Camera camera, RenderSettings settings,
                                   double radius, LevelLightEngine light, int minSectionY, boolean hasSkyLight) {
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            int sy = minSectionY + i;
            if (!isRelevant(camera, settings, radius, column.x(), sy, column.z())) {
                continue;
            }
            LevelChunkSection section = sections[i];
            SectionPos pos = SectionPos.of(column.x(), sy, column.z());
            snapshot.put(new SectionSnapshot(
                column.x(), sy, column.z(),
                section.hasOnlyAir() ? null : section.getStates().copy(),
                section.getBiomes().copy(),
                hasSkyLight ? copyLayer(light, LightLayer.SKY, pos) : null,
                copyLayer(light, LightLayer.BLOCK, pos)
            ));
        }
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

    private static boolean isRelevant(Camera camera, RenderSettings settings, double radius, int cx, int sy, int cz) {
        double dx = (cx << 4) + 8 - settings.position().get(0);
        double dy = (sy << 4) + 8 - settings.position().get(1);
        double dz = (cz << 4) + 8 - settings.position().get(2);
        return dx * dx + dy * dy + dz * dz <= radius * radius && camera.intersectsSection(cx, sy, cz);
    }

    private static DataLayer copyLayer(LevelLightEngine engine, LightLayer layer, SectionPos pos) {
        DataLayer data = engine.getLayerListener(layer).getDataLayerData(pos);
        return data == null ? null : data.copy();
    }
}
