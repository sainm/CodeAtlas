package org.sainm.codeatlas.graph;

import java.util.List;

public record CallerTraversalResult(
        String changedMethodId,
        List<ImpactPath> callerPaths,
        boolean truncated) {
    public CallerTraversalResult {
        if (changedMethodId == null || changedMethodId.isBlank()) {
            throw new IllegalArgumentException("changedMethodId is required");
        }
        callerPaths = List.copyOf(callerPaths == null ? List.of() : callerPaths);
    }
}
