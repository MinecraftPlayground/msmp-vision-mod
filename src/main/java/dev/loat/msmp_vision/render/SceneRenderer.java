package dev.loat.msmp_vision.render;

import dev.loat.msmp_vision.assets.AssetManager;
import dev.loat.msmp_vision.assets.TextureData;
import dev.loat.msmp_vision.assets.TextureStore;
import dev.loat.msmp_vision.config.files.MSMPVisionConfigFile;
import dev.loat.msmp_vision.mesh.SectionGeometry;
import dev.loat.msmp_vision.mesh.SectionMesher;
import dev.loat.msmp_vision.mesh.TintResolver;
import dev.loat.msmp_vision.model.BlockModelBaker;
import dev.loat.msmp_vision.model.BlockStateSelector;
import dev.loat.msmp_vision.model.ModelResolver;
import dev.loat.msmp_vision.raster.Fog;
import dev.loat.msmp_vision.raster.Framebuffer;
import dev.loat.msmp_vision.raster.GeometryProjector;
import dev.loat.msmp_vision.raster.TileRasterizer;
import dev.loat.msmp_vision.raster.TriangleBuffer;
import dev.loat.msmp_vision.world.SectionSnapshot;
import dev.loat.msmp_vision.world.WorldSnapshot;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Orchestrates one render: mesh and project sections in parallel, paint the sky, rasterize tiles in parallel.
 * Thread-safe; baked models and textures are cached across renders.
 */
public final class SceneRenderer {

    private record Projected(TriangleBuffer opaque, TriangleBuffer translucent) {}

    private final TextureStore textures;
    private final RenderExecutor executor;
    private final BlockModelBaker baker;
    private final TintResolver tint = new TintResolver();

    /**
     * Creates a renderer.
     *
     * @param assets Asset source.
     * @param textures Texture registry.
     * @param executor Worker pool.
     */
    public SceneRenderer(AssetManager assets, TextureStore textures, RenderExecutor executor) {
        this.textures = textures;
        this.executor = executor;
        this.baker = new BlockModelBaker(new BlockStateSelector(assets), new ModelResolver(assets), textures);
    }

    /**
     * Renders a captured world.
     *
     * @param world The snapshot.
     * @param camera The camera.
     * @param settings The settings.
     * @param config The current configuration.
     * @return The final image.
     */
    public BufferedImage render(WorldSnapshot world, Camera camera, RenderSettings settings, MSMPVisionConfigFile config) {
        long timeout = config.requestTimeoutSeconds;
        int width = settings.renderWidth();
        int height = settings.renderHeight();
        boolean cull = config.rendering.backfaceCulling;

        List<CompletableFuture<Projected>> jobs = new ArrayList<>();
        for (SectionSnapshot section : world.sections()) {
            if (section.isEmpty()) {
                continue;
            }
            jobs.add(CompletableFuture.supplyAsync(() -> {
                SectionGeometry geometry = new SectionMesher(world, this.baker, this.textures, this.tint, camera, settings.biomeBlend()).mesh(section);
                GeometryProjector projector = new GeometryProjector(camera.viewProjection(), width, height, cull);
                return new Projected(projector.project(geometry.opaque()), projector.project(geometry.translucent()));
            }, this.executor.executor()));
        }
        this.executor.awaitAll(jobs, timeout);

        List<TriangleBuffer> opaque = new ArrayList<>(jobs.size());
        List<TriangleBuffer> translucent = new ArrayList<>(jobs.size());
        for (CompletableFuture<Projected> job : jobs) {
            Projected projected = job.join();
            if (projected.opaque().count() > 0) opaque.add(projected.opaque());
            if (projected.translucent().count() > 0) translucent.add(projected.translucent());
        }

        Framebuffer framebuffer = new Framebuffer(width, height);
        SkyPainter.paint(framebuffer, camera, world.environment());

        float fogEnd = settings.renderDistance();
        Fog fog = Fog.of((float) (fogEnd * config.rendering.fogStartFactor), fogEnd, world.environment().fogColor());
        new TileRasterizer(this.executor, config.rendering.tileSize, timeout)
            .rasterize(TriangleBuffer.merge(opaque), TriangleBuffer.merge(translucent), framebuffer, this.texturesById(), fog);
        return framebuffer.toImage(settings.ssaa());
    }

    private TextureData[] texturesById() {
        TextureData[] all = this.textures.snapshot();
        int max = -1;
        for (TextureData texture : all) {
            if (texture != null) {
                max = Math.max(max, texture.id());
            }
        }
        TextureData[] byId = new TextureData[max + 1];
        for (TextureData texture : all) {
            if (texture != null) {
                byId[texture.id()] = texture;
            }
        }
        return byId;
    }
}
