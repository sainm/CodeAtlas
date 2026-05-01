package org.sainm.codeatlas.graph.benchmark;

import java.util.ArrayList;
import java.util.List;

public record FfmActivationPolicy(
    long minEdgeCount,
    double minP95Millis,
    long minHeapBytes
) {
    public FfmActivationPolicy {
        minEdgeCount = Math.max(1L, minEdgeCount);
        minP95Millis = Math.max(1.0d, minP95Millis);
        minHeapBytes = Math.max(1L, minHeapBytes);
    }

    public static FfmActivationPolicy defaultPolicy() {
        return new FfmActivationPolicy(1_000_000L, 250.0d, 512L * 1024L * 1024L);
    }

    public FfmActivationDecision evaluate(GraphScaleMetrics metrics) {
        if (metrics == null) {
            throw new IllegalArgumentException("metrics is required");
        }
        List<String> reasons = new ArrayList<>();
        if (metrics.edgeCount() < minEdgeCount) {
            reasons.add("edgeCount below threshold");
        }
        if (metrics.p95Millis() < minP95Millis) {
            reasons.add("p95Millis below threshold");
        }
        if (metrics.estimatedHeapBytes() < minHeapBytes) {
            reasons.add("estimatedHeapBytes below threshold");
        }
        if (reasons.isEmpty()) {
            return new FfmActivationDecision(
                true,
                "FFM candidate: graph scale, P95 latency, and heap estimate meet thresholds",
                List.of(
                    "edgeCount meets threshold",
                    "p95Millis meets threshold",
                    "estimatedHeapBytes meets threshold"
                )
            );
        }
        return new FfmActivationDecision(false, "Keep JVM adjacency cache until benchmark thresholds are met", reasons);
    }
}
