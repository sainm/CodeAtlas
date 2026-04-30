package org.sainm.codeatlas.graph.benchmark;

import java.util.Arrays;

public final class BenchmarkTimer {
    public BenchmarkResult measure(
        String name,
        int warmupIterations,
        int iterations,
        Runnable operation,
        long estimatedHeapBytes
    ) {
        if (operation == null) {
            throw new IllegalArgumentException("operation is required");
        }
        if (iterations <= 0) {
            throw new IllegalArgumentException("iterations must be positive");
        }
        for (int i = 0; i < Math.max(0, warmupIterations); i++) {
            operation.run();
        }

        long[] nanos = new long[iterations];
        long total = 0L;
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            operation.run();
            long elapsed = Math.max(0L, System.nanoTime() - start);
            nanos[i] = elapsed;
            total += elapsed;
        }

        long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        int p95Index = Math.min(sorted.length - 1, (int) Math.ceil(sorted.length * 0.95) - 1);
        return new BenchmarkResult(
            name,
            iterations,
            sorted[p95Index] / 1_000_000.0,
            (total / (double) iterations) / 1_000_000.0,
            total,
            estimatedHeapBytes
        );
    }
}
