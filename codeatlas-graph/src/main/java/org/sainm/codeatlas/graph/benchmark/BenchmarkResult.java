package org.sainm.codeatlas.graph.benchmark;

public record BenchmarkResult(
    String name,
    int iterations,
    double p95Millis,
    double averageMillis,
    long totalNanos,
    long estimatedHeapBytes
) {
    public BenchmarkResult {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (iterations <= 0) {
            throw new IllegalArgumentException("iterations must be positive");
        }
        p95Millis = Math.max(0.0, p95Millis);
        averageMillis = Math.max(0.0, averageMillis);
        totalNanos = Math.max(0L, totalNanos);
        estimatedHeapBytes = Math.max(0L, estimatedHeapBytes);
        name = name.trim();
    }
}
