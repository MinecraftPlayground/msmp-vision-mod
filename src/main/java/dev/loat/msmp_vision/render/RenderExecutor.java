package dev.loat.msmp_vision.render;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Daemon thread pool used for meshing and rasterization.
 */
public final class RenderExecutor {

    private final ExecutorService pool;
    private final int threads;

    /**
     * Creates the pool.
     *
     * @param configuredThreads Thread count, {@code 0} selects the number of available processors.
     */
    public RenderExecutor(int configuredThreads) {
        this.threads = configuredThreads > 0 ? configuredThreads : Math.max(1, Runtime.getRuntime().availableProcessors());
        AtomicInteger counter = new AtomicInteger();
        this.pool = Executors.newFixedThreadPool(this.threads, runnable -> {
            Thread thread = new Thread(runnable, "msmp-vision-render-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    /** @return The underlying executor. */
    public ExecutorService executor() { return this.pool; }

    /** @return Number of worker threads. */
    public int threads() { return this.threads; }

    /**
     * Waits for all jobs, cancelling the rest on timeout or failure.
     *
     * @param jobs The jobs.
     * @param timeoutSeconds Maximum wait.
     * @throws IllegalStateException on timeout, failure or interruption.
     */
    public void awaitAll(List<? extends CompletableFuture<?>> jobs, long timeoutSeconds) {
        try {
            CompletableFuture.allOf(jobs.toArray(new CompletableFuture[0])).get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            jobs.forEach(job -> job.cancel(true));
            throw new IllegalStateException("Rendering timed out after " + timeoutSeconds + "s");
        } catch (ExecutionException e) {
            jobs.forEach(job -> job.cancel(true));
            throw new IllegalStateException("Rendering failed: " + e.getCause(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            jobs.forEach(job -> job.cancel(true));
            throw new IllegalStateException("Rendering interrupted");
        }
    }

    /** Stops all worker threads. */
    public void shutdown() {
        this.pool.shutdownNow();
    }
}
