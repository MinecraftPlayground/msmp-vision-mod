package dev.loat.msmp_vision.render;

import dev.loat.msmp_vision.assets.AssetManager;
import dev.loat.msmp_vision.assets.TextureData;
import dev.loat.msmp_vision.assets.TextureStore;
import dev.loat.msmp_vision.config.files.MSMPVisionConfigFile;
import dev.loat.msmp_vision.mesh.QuadBuffer;
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
import dev.loat.msmp_vision.world.EnvironmentSnapshot;
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
    private final CloudMask cloudMask;
    private final CelestialTextures celestial;
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
        this.cloudMask = new CloudMask(assets);
        this.celestial = new CelestialTextures(assets);
    }

    /**
     * Renders a captured world.
     *
     * @param world The snapshot.
     * @param camera The camera.
     * @param settings The settings.
     * @param config The current configuration.
     * @return The final image together with phase timings.
     */
    public Rendered render(WorldSnapshot world, Camera camera, RenderSettings settings, MSMPVisionConfigFile config) {
        long startNanos = System.nanoTime();
        long timeout = config.requestTimeoutSeconds;
        int width = settings.renderWidth();
        int height = settings.renderHeight();
        boolean cull = config.render.backfaceCulling;

        List<CompletableFuture<Projected>> jobs = new ArrayList<>();
        for (SectionSnapshot section : world.sections()) {
            if (section.isEmpty()) {
                continue;
            }
            jobs.add(CompletableFuture.supplyAsync(() -> {
                SectionGeometry geometry = new SectionMesher(world, this.baker, this.textures, this.tint, camera, settings.biomeBlend(), config.render.smoothLighting, (float) settings.brightness(), (float) config.render.blockLightFactor, config.render.lightGammaPerChannel).mesh(section);
                GeometryProjector projector = new GeometryProjector(camera.viewProjection(), width, height, cull);
                return new Projected(projector.project(geometry.opaque()), projector.project(geometry.translucent()));
            }, this.executor.executor()));
        }
        this.executor.awaitAll(jobs, timeout);
        long meshedNanos = System.nanoTime();

        List<TriangleBuffer> opaque = new ArrayList<>(jobs.size());
        List<TriangleBuffer> translucent = new ArrayList<>(jobs.size());
        for (CompletableFuture<Projected> job : jobs) {
            Projected projected = job.join();
            if (projected.opaque().count() > 0) opaque.add(projected.opaque());
            if (projected.translucent().count() > 0) translucent.add(projected.translucent());
        }

        Framebuffer framebuffer = new Framebuffer(width, height);
        long skyStart = System.nanoTime();
        SkyPainter.paint(framebuffer, camera, world.environment(), this.executor, timeout,
            this.celestial.sun(), this.celestial.moon(world.environment().moonPhase()));
        long skyDone = System.nanoTime();

        float fogEnd = settings.renderDistanceBlocks();
        Fog fog = Fog.of((float) (fogEnd * config.render.fogStartFactor), fogEnd, world.environment().fogColor(),
            camera.tanHalfFov(), camera.aspect(), width, height);
        if (config.render.environmentalFog) {
            fog = fog.withEnvironment(world.environment().environmentFogStart(), world.environment().environmentFogEnd(),
                new float[]{camera.forward().x, camera.forward().y, camera.forward().z},
                new float[]{camera.right().x, camera.right().y, camera.right().z},
                new float[]{camera.up().x, camera.up().y, camera.up().z});
        }
        TriangleBuffer clouds = this.cloudTriangles(world.environment(), camera, settings, config, width, height, cull);
        // The clouds fade out with the distance (alpha from the cloud colour at the camera to 0 at the cloud fog end).
        Fog cloudFog = Fog.of(0f, world.environment().cloudFogEnd(), world.environment().fogColor(),
            camera.tanHalfFov(), camera.aspect(), width, height).asAlpha();
        new TileRasterizer(this.executor, config.render.tileSize, timeout, settings.mipmapLevels())
            .rasterize(TriangleBuffer.merge(opaque), TriangleBuffer.merge(translucent), clouds, framebuffer, this.texturesById(), fog, cloudFog);
        long tilesDone = System.nanoTime();
        BufferedImage image = framebuffer.toImage(settings.ssaa());
        long doneNanos = System.nanoTime();
        return new Rendered(image, meshedNanos - startNanos, skyDone - skyStart, tilesDone - skyDone, doneNanos - tilesDone);
    }

    /** Builds the cloud layer as screen-space triangles; empty if clouds are off, not available or the dimension has none. */
    private TriangleBuffer cloudTriangles(EnvironmentSnapshot environment, Camera camera, RenderSettings settings,
                                          MSMPVisionConfigFile config, int width, int height, boolean cull) {
        if (!config.clouds.enabled || !environment.clouds()) {
            return new TriangleBuffer();
        }
        CloudMask.Mask mask = this.cloudMask.get();
        if (mask == null) {
            return new TriangleBuffer();
        }
        int alpha = environment.cloudColor() >>> 24;
        TextureData texture = this.textures.solid("cloud-" + alpha, (alpha << 24) | 0xFFFFFF);
        int cloudRgb = environment.cloudColor();
        float brightness = (((cloudRgb >> 16) & 0xFF) + ((cloudRgb >> 8) & 0xFF) + (cloudRgb & 0xFF)) / (3f * 255f);
        QuadBuffer quads = new QuadBuffer();
        CloudGeometry.build(quads, texture.id(), mask.cells(), mask.width(), mask.height(),
            settings.position().get(0), settings.position().get(1), settings.position().get(2),
            camera.originX(), camera.originY(), camera.originZ(),
            environment.gameTime() * CloudGeometry.SPEED, config.clouds.height, config.clouds.distance, brightness);
        return new GeometryProjector(camera.viewProjection(), width, height, cull).project(quads);
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
