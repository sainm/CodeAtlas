package org.sainm.codeatlas.graph.benchmark;

public record GraphScaleMetrics(
    long edgeCount,
    double p95Millis,
    long estimatedHeapBytes
) {
    public GraphScaleMetrics {
        edgeCount = Math.max(0L, edgeCount);
        p95Millis = Math.max(0.0d, p95Millis);
        estimatedHeapBytes = Math.max(0L, estimatedHeapBytes);
    }
}
