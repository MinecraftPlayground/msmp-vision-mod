package dev.loat.msmp_vision;

import dev.loat.msmp_vision.assets.AssetManager;
import dev.loat.msmp_vision.assets.TextureStore;
import dev.loat.msmp_vision.config.Config;
import dev.loat.msmp_vision.config.files.MSMPVisionConfigFile;
import dev.loat.msmp_vision.encode.ImageEncoder;
import dev.loat.msmp_vision.logging.Logger;
import dev.loat.msmp_vision.mesh.TintResolver;
import dev.loat.msmp_vision.msmp.endpoints.screenshot.ScreenshotRequest;
import dev.loat.msmp_vision.msmp.endpoints.screenshot.ScreenshotResponse;
import dev.loat.msmp_vision.msmp.exceptions.UnknownDimensionException;
import dev.loat.msmp_vision.render.Camera;
import dev.loat.msmp_vision.render.RenderExecutor;
import dev.loat.msmp_vision.render.RenderSettings;
import dev.loat.msmp_vision.render.SceneRenderer;
import dev.loat.msmp_vision.world.SnapshotCapture;
import dev.loat.msmp_vision.world.WorldSnapshot;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Handles screenshot requests: validates, captures the world on the server thread and renders on worker threads.
 */
public final class VisionService {

    private record Runtime(
        RenderExecutor executor,
        SceneRenderer renderer,
        CompletableFuture<Void> ready
    ) {}

    private volatile Runtime runtime;

    /**
     * Starts asset preparation and the render pool. Call on server start.
     */
    public void start() {
        this.stop();
        MSMPVisionConfigFile config = Config.getConfig();
        Path root = FabricLoader.getInstance().getConfigDir().resolve("msmp/vision/client_assets");
        AssetManager assets = new AssetManager(root, FabricLoader.getInstance().getRawGameVersion());
        TextureStore textures = new TextureStore(assets);
        RenderExecutor executor = new RenderExecutor(config.render.threads);
        SceneRenderer renderer = new SceneRenderer(assets, textures, executor);

        CompletableFuture<Void> ready = assets.initialize().thenRun(() -> TintResolver.initializeColorMaps(assets));
        ready.whenComplete((ignored, error) -> {
            if (error != null) {
                Logger.error("Failed to prepare client assets: " + error);
            } else {
                Logger.info("Client assets ready (" + executor.threads() + " render threads).");
            }
        });
        this.runtime = new Runtime(executor, renderer, ready);
    }

    /** Stops the render pool. Call on server stop. */
    public void stop() {
        Runtime current = this.runtime;
        this.runtime = null;
        if (current != null) {
            current.executor().shutdown();
        }
    }

    /**
     * Renders a screenshot.
     *
     * @param server The server.
     * @param request The request.
     * @return The response containing a Base64 PNG.
     * @throws dev.loat.msmp_vision.msmp.exceptions.InvalidParamsException for invalid parameters.
     * @throws UnknownDimensionException if the requested dimension does not exist.
     * @throws IllegalStateException if the renderer is not ready or rendering failed.
     */
    public ScreenshotResponse screenshot(MinecraftServer server, ScreenshotRequest request) {
        Runtime current = this.runtime;
        if (current == null) {
            throw new IllegalStateException("The renderer is not running.");
        }
        if (!current.ready().isDone()) {
            throw new IllegalStateException("Client assets are still being prepared, try again shortly.");
        }
        if (current.ready().isCompletedExceptionally()) {
            throw new IllegalStateException("Client assets could not be prepared, see the server log.");
        }

        MSMPVisionConfigFile config = Config.getConfig();
        RenderSettings settings = RenderSettings.from(request, config);
        ServerLevel level = this.resolveLevel(server, request.dimension());

        long start = System.nanoTime();
        Camera camera = new Camera(settings);
        WorldSnapshot snapshot = this.capture(server, level, camera, settings, config);
        BufferedImage image = current.renderer().render(snapshot, camera, settings, config);
        String encoded = ImageEncoder.toBase64Png(image);
        int elapsed = (int) ((System.nanoTime() - start) / 1_000_000L);

        return new ScreenshotResponse(encoded, "png", image.getWidth(), image.getHeight(), elapsed);
    }

    /**
     * Captures the world on the server thread. MSMP handlers already run there, in which case the capture
     * (including loading missing chunks) is done directly; otherwise it is scheduled on the server thread.
     */
    private WorldSnapshot capture(MinecraftServer server, ServerLevel level, Camera camera, RenderSettings settings, MSMPVisionConfigFile config) {
        if (server.isSameThread()) {
            return SnapshotCapture.capture(level, camera, settings, config.chunks);
        }
        long timeoutSeconds = config.requestTimeoutSeconds + config.chunks.loadTimeoutSeconds;
        try {
            return CompletableFuture.supplyAsync(() -> SnapshotCapture.capture(level, camera, settings, config.chunks), server)
                .get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException("The server thread did not respond in time.");
        } catch (ExecutionException e) {
            throw new IllegalStateException("World capture failed: " + e.getCause(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while capturing the world.");
        }
    }

    private ServerLevel resolveLevel(MinecraftServer server, Optional<String> dimension) {
        if (dimension.isEmpty()) {
            return server.overworld();
        }
        Identifier identifier = Identifier.tryParse(dimension.get());
        if (identifier == null) {
            throw new UnknownDimensionException(dimension.get());
        }
        ServerLevel level = server.getLevel(ResourceKey.<Level>create(Registries.DIMENSION, identifier));
        if (level == null) {
            throw new UnknownDimensionException(dimension.get());
        }
        return level;
    }
}
