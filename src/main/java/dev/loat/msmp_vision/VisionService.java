package dev.loat.msmp_vision;

import dev.loat.msmp_vision.assets.AssetManager;
import dev.loat.msmp_vision.assets.TextureStore;
import dev.loat.msmp_vision.config.Config;
import dev.loat.msmp_vision.config.files.MSMPVisionConfigFile;
import dev.loat.msmp_vision.encode.ImageEncoder;
import dev.loat.msmp_vision.logging.Logger;
import dev.loat.msmp_vision.mesh.TintResolver;
import dev.loat.msmp_vision.msmp.endpoints.screenshot.ScreenshotNotifier;
import dev.loat.msmp_vision.msmp.endpoints.screenshot.ScreenshotRequest;
import dev.loat.msmp_vision.msmp.endpoints.screenshot.ScreenshotResponse;
import dev.loat.msmp_vision.msmp.exceptions.QueueFullException;
import dev.loat.msmp_vision.msmp.exceptions.UnknownDimensionException;
import dev.loat.msmp_vision.render.Camera;
import dev.loat.msmp_vision.render.RenderExecutor;
import dev.loat.msmp_vision.render.RenderSettings;
import dev.loat.msmp_vision.render.Rendered;
import dev.loat.msmp_vision.render.SceneRenderer;
import dev.loat.msmp_vision.world.SnapshotSession;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles screenshot requests: validates, captures the world on the server thread and renders on worker threads.
 *
 * <p>Two ways to take a screenshot:</p>
 * <ul>
 *   <li>{@link #screenshot}: synchronous. Blocks the server thread until the image is ready.</li>
 *   <li>{@link #queue} + {@link #tick}: asynchronous. The world is captured in small slices over several ticks,
 *   rendering and PNG encoding happen off the server thread and the result is sent as a notification.</li>
 * </ul>
 */
public final class VisionService {

    private record Runtime(
        RenderExecutor executor,
        ExecutorService coordinator,
        SceneRenderer renderer,
        CompletableFuture<Void> ready
    ) {}

    private record Prepared(RenderSettings settings, ServerLevel level, Camera camera) {}

    private static final class Job {
        final String id = UUID.randomUUID().toString();
        final Integer connectionId;
        final Prepared prepared;
        final long createdNanos = System.nanoTime();
        SnapshotSession session;

        Job(Integer connectionId, Prepared prepared) {
            this.connectionId = connectionId;
            this.prepared = prepared;
        }
    }

    private volatile Runtime runtime;
    private volatile ScreenshotNotifier notifier;
    private final Queue<Job> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pending = new AtomicInteger();

    /**
     * Sets the notifier used to deliver queued screenshots. Call once during initialization.
     *
     * @param notifier The notifier.
     */
    public void bindNotifier(ScreenshotNotifier notifier) {
        this.notifier = notifier;
    }

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
        ExecutorService coordinator = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "msmp-vision-coordinator");
            thread.setDaemon(true);
            return thread;
        });

        CompletableFuture<Void> ready = assets.initialize().thenRun(() -> TintResolver.initializeColorMaps(assets));
        ready.whenComplete((ignored, error) -> {
            if (error != null) {
                Logger.error("Failed to prepare client assets: " + error);
            } else {
                Logger.info("Client assets ready (" + executor.threads() + " render threads).");
            }
        });
        this.runtime = new Runtime(executor, coordinator, renderer, ready);
    }

    /** Stops the render pool and drops all queued screenshots. Call on server stop. */
    public void stop() {
        Runtime current = this.runtime;
        this.runtime = null;
        this.queue.clear();
        this.pending.set(0);
        if (current != null) {
            current.coordinator().shutdownNow();
            current.executor().shutdown();
        }
    }

    /**
     * Renders a screenshot and blocks until it is ready.
     *
     * @param server The server.
     * @param request The request.
     * @return The response containing a Base64 PNG.
     * @throws dev.loat.msmp_vision.msmp.exceptions.InvalidParamsException for invalid parameters.
     * @throws UnknownDimensionException if the requested dimension does not exist.
     * @throws IllegalStateException if the renderer is not ready or rendering failed.
     */
    public ScreenshotResponse screenshot(MinecraftServer server, ScreenshotRequest request) {
        Runtime current = this.requireReady();
        MSMPVisionConfigFile config = Config.getConfig();
        Prepared prepared = this.prepare(server, request, config);

        long start = System.nanoTime();
        SnapshotSession session = this.captureBlocking(server, prepared, config);
        Rendered rendered = current.renderer().render(session.snapshot(), prepared.camera(), prepared.settings(), config);
        long encodeStart = System.nanoTime();
        String encoded = ImageEncoder.toBase64Png(rendered.image());
        long end = System.nanoTime();

        logTimings(session.workNanos(), rendered, end - encodeStart, end - start);
        return new ScreenshotResponse(encoded, "png", rendered.image().getWidth(), rendered.image().getHeight(), (int) ((end - start) / 1_000_000L));
    }

    /**
     * Queues a screenshot. The call returns immediately; the result is delivered with a notification to the
     * given connection, see {@link #tick(MinecraftServer)}.
     *
     * @param server The server.
     * @param connectionId The connection to notify.
     * @param request The request.
     * @return The id of the queued screenshot.
     * @throws QueueFullException if too many screenshots are pending.
     * @throws dev.loat.msmp_vision.msmp.exceptions.InvalidParamsException for invalid parameters.
     * @throws UnknownDimensionException if the requested dimension does not exist.
     * @throws IllegalStateException if the renderer is not ready.
     */
    public String queue(MinecraftServer server, Integer connectionId, ScreenshotRequest request) {
        this.requireReady();
        if (this.notifier == null) {
            throw new IllegalStateException("Notifications are not available.");
        }
        MSMPVisionConfigFile config = Config.getConfig();
        int limit = config.limits.maxPendingScreenshots;
        if (this.pending.get() >= limit) {
            throw new QueueFullException(limit);
        }

        Job job = new Job(connectionId, this.prepare(server, request, config));
        this.pending.incrementAndGet();
        this.queue.add(job);
        return job.id;
    }

    /**
     * Advances the oldest queued screenshot by one slice. Call once per server tick (on the server thread).
     *
     * @param server The server.
     */
    public void tick(MinecraftServer server) {
        Job job = this.queue.peek();
        if (job == null) {
            return;
        }
        try {
            Runtime current = this.runtime;
            if (current == null) {
                throw new IllegalStateException("The renderer is not running.");
            }
            MSMPVisionConfigFile config = Config.getConfig();
            long timeoutNanos = TimeUnit.SECONDS.toNanos(config.requestTimeoutSeconds + config.chunks.loadTimeoutSeconds);
            if (System.nanoTime() - job.createdNanos > timeoutNanos) {
                throw new IllegalStateException("Timed out while capturing the world.");
            }
            if (job.session == null) {
                job.session = new SnapshotSession(job.prepared.level(), job.prepared.camera(), job.prepared.settings(), config.chunks);
            }
            long budget = TimeUnit.MILLISECONDS.toNanos(Math.max(1, config.chunks.tickBudgetMs));
            if (!job.session.step(budget)) {
                return;
            }
            this.queue.remove(job);
            this.render(server, current, job);
        } catch (RuntimeException e) {
            this.queue.remove(job);
            this.fail(job, e);
        }
    }

    /** Hands a captured screenshot over to the coordinator thread, which renders and encodes it. */
    private void render(MinecraftServer server, Runtime current, Job job) {
        SnapshotSession session = job.session;
        current.coordinator().execute(() -> {
            try {
                MSMPVisionConfigFile config = Config.getConfig();
                Rendered rendered = current.renderer().render(session.snapshot(), job.prepared.camera(), job.prepared.settings(), config);
                long encodeStart = System.nanoTime();
                String encoded = ImageEncoder.toBase64Png(rendered.image());
                long end = System.nanoTime();

                logTimings(session.workNanos(), rendered, end - encodeStart, end - job.createdNanos);
                ScreenshotResponse response = new ScreenshotResponse(
                    encoded, "png", rendered.image().getWidth(), rendered.image().getHeight(), (int) ((end - job.createdNanos) / 1_000_000L)
                );
                server.execute(() -> {
                    this.pending.decrementAndGet();
                    this.notifier.completed(job.connectionId, job.id, response);
                });
            } catch (Throwable error) {
                server.execute(() -> this.fail(job, error));
            }
        });
    }

    private void fail(Job job, Throwable error) {
        this.pending.decrementAndGet();
        String message = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
        Logger.warning("Queued screenshot %s failed: %s".formatted(job.id, message));
        ScreenshotNotifier current = this.notifier;
        if (current != null) {
            current.failed(job.connectionId, job.id, message);
        }
    }

    private Runtime requireReady() {
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
        return current;
    }

    private Prepared prepare(MinecraftServer server, ScreenshotRequest request, MSMPVisionConfigFile config) {
        RenderSettings settings = RenderSettings.from(request, config);
        ServerLevel level = this.resolveLevel(server, request.dimension());
        return new Prepared(settings, level, new Camera(settings));
    }

    /**
     * Captures the whole world at once on the server thread. MSMP handlers already run there, in which case the
     * capture (including loading missing chunks) is done directly; otherwise it is scheduled on the server thread.
     */
    private SnapshotSession captureBlocking(MinecraftServer server, Prepared prepared, MSMPVisionConfigFile config) {
        if (server.isSameThread()) {
            return captureAll(prepared, config);
        }
        long timeoutSeconds = config.requestTimeoutSeconds + config.chunks.loadTimeoutSeconds;
        try {
            return CompletableFuture.supplyAsync(() -> captureAll(prepared, config), server).get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException("The server thread did not respond in time.");
        } catch (ExecutionException e) {
            throw new IllegalStateException("World capture failed: " + e.getCause(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while capturing the world.");
        }
    }

    private static SnapshotSession captureAll(Prepared prepared, MSMPVisionConfigFile config) {
        SnapshotSession session = new SnapshotSession(prepared.level(), prepared.camera(), prepared.settings(), config.chunks);
        session.step(Long.MAX_VALUE);
        return session;
    }

    private static void logTimings(long captureNanos, Rendered rendered, long encodeNanos, long totalNanos) {
        Logger.info("Screenshot timings: capture %d ms (server thread), mesh %d ms, raster %d ms, encode %d ms, total %d ms."
            .formatted(captureNanos / 1_000_000L, rendered.meshNanos() / 1_000_000L, rendered.rasterNanos() / 1_000_000L,
                encodeNanos / 1_000_000L, totalNanos / 1_000_000L));
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
