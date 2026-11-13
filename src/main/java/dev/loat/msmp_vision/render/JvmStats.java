package dev.loat.msmp_vision.render;

import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

/**
 * Garbage collection and JIT compilation counters, to tell why identical requests take different times.
 */
public final class JvmStats {

    /**
     * @param gcMillis Total time spent in garbage collections so far.
     * @param gcCount Total number of garbage collections so far.
     * @param jitMillis Total time the JIT compiler has spent so far (summed over all compiler threads).
     */
    public record Sample(long gcMillis, long gcCount, long jitMillis) {}

    private JvmStats() {}

    /** @return The current counters. */
    public static Sample sample() {
        long gcTime = 0;
        long gcCount = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcTime += Math.max(0, bean.getCollectionTime());
            gcCount += Math.max(0, bean.getCollectionCount());
        }
        CompilationMXBean compilation = ManagementFactory.getCompilationMXBean();
        long jit = compilation != null && compilation.isCompilationTimeMonitoringSupported() ? compilation.getTotalCompilationTime() : 0;
        return new Sample(gcTime, gcCount, jit);
    }

    /**
     * Describes what happened since {@code before}.
     *
     * @param before An earlier sample.
     * @return For example {@code "jvm: gc 120 ms (2x), jit 340 ms, heap 612/2048 MB"}.
     */
    public static String since(Sample before) {
        Sample now = sample();
        Runtime runtime = Runtime.getRuntime();
        long used = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long max = runtime.maxMemory() / (1024 * 1024);
        return "jvm: gc %d ms (%dx), jit %d ms, heap %d/%d MB".formatted(
            now.gcMillis() - before.gcMillis(), now.gcCount() - before.gcCount(), now.jitMillis() - before.jitMillis(), used, max);
    }
}
