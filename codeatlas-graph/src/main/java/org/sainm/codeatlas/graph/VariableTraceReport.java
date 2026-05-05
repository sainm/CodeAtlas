package org.sainm.codeatlas.graph;

import java.util.List;

public record VariableTraceReport(
        String mode,
        String startIdentityId,
        List<ImpactPath> sourcePaths,
        List<ImpactPath> sinkPaths,
        List<ImpactPath> combinedPaths,
        boolean truncated) {
    public VariableTraceReport {
        if (mode == null || mode.isBlank()) {
            throw new IllegalArgumentException("mode is required");
        }
        if (startIdentityId == null || startIdentityId.isBlank()) {
            throw new IllegalArgumentException("startIdentityId is required");
        }
        sourcePaths = List.copyOf(sourcePaths == null ? List.of() : sourcePaths);
        sinkPaths = List.copyOf(sinkPaths == null ? List.of() : sinkPaths);
        combinedPaths = List.copyOf(combinedPaths == null ? List.of() : combinedPaths);
    }
}
