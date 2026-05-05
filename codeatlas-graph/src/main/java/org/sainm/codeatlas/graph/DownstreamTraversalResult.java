package org.sainm.codeatlas.graph;

import java.util.List;

public record DownstreamTraversalResult(
        String entrypointId,
        List<ImpactPath> downstreamPaths,
        boolean truncated) {
    public DownstreamTraversalResult {
        if (entrypointId == null || entrypointId.isBlank()) {
            throw new IllegalArgumentException("entrypointId is required");
        }
        downstreamPaths = List.copyOf(downstreamPaths == null ? List.of() : downstreamPaths);
    }
}
