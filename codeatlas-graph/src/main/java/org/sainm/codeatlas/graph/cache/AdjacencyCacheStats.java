package org.sainm.codeatlas.graph.cache;

public record AdjacencyCacheStats(
    int nodeCount,
    int edgeCount,
    long estimatedHeapBytes,
    long hits,
    long misses
) {
    public double hitRatio() {
        long total = hits + misses;
        return total == 0 ? 0.0 : (double) hits / total;
    }
}
