package dev.loat.msmp_vision.world;

import dev.loat.msmp_vision.render.Camera;
import dev.loat.msmp_vision.render.RenderSettings;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Copies the chunk sections around the camera into a {@link WorldSnapshot}. Must run on the server thread.
 */
public final class SnapshotCapture {

    private SnapshotCapture() {}

    /**
     * Captures all loaded, potentially visible sections.
     *
     * @param level The level.
     * @param camera The camera used for frustum culling.
     * @param settings The render settings.
     * @return The snapshot.
     */
    public static WorldSnapshot capture(ServerLevel level, Camera camera, RenderSettings settings) {
        Vec3 position = new Vec3(settings.position().get(0), settings.position().get(1), settings.position().get(2));
        Vector3f fog = new Vector3f(level.environmentAttributes().getValue(EnvironmentAttributes.FOG_COLOR, position)).mul(255.0f);
        EnvironmentSnapshot environment = new EnvironmentSnapshot(
            level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, position) * Mth.DEG_TO_RAD,
            level.environmentAttributes().getValue(EnvironmentAttributes.SKY_LIGHT_FACTOR, position),
            (int) fog.x << 16 | (int) fog.y << 8 | (int) fog.z,
            level.dimensionType().hasSkyLight()
        );
        WorldSnapshot snapshot = new WorldSnapshot(environment);

        double radius = settings.renderDistance() + 16;
        int minChunkX = Mth.floor(settings.position().get(0) - radius) >> 4;
        int maxChunkX = Mth.floor(settings.position().get(0) + radius) >> 4;
        int minChunkZ = Mth.floor(settings.position().get(2) - radius) >> 4;
        int maxChunkZ = Mth.floor(settings.position().get(2) + radius) >> 4;
        int minSectionY = level.getMinSectionY();
        LevelLightEngine light = level.getLightEngine();

        for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    int sy = minSectionY + i;
                    if (!isRelevant(camera, settings, radius, cx, sy, cz)) {
                        continue;
                    }
                    LevelChunkSection section = sections[i];
                    SectionPos pos = SectionPos.of(cx, sy, cz);
                    snapshot.put(new SectionSnapshot(
                        cx, sy, cz,
                        section.hasOnlyAir() ? null : section.getStates().copy(),
                        section.getBiomes().copy(),
                        environment.hasSkyLight() ? copyLayer(light, LightLayer.SKY, pos) : null,
                        copyLayer(light, LightLayer.BLOCK, pos)
                    ));
                }
            }
        }
        return snapshot;
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
