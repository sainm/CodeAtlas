package org.sainm.codeatlas.graph;

import java.util.List;

public record WebBackendFlowSearchResult(
        String sourceIdentityId,
        List<ImpactPath> backendFlowPaths,
        boolean truncated) {
    public WebBackendFlowSearchResult {
        if (sourceIdentityId == null || sourceIdentityId.isBlank()) {
            throw new IllegalArgumentException("sourceIdentityId is required");
        }
        backendFlowPaths = List.copyOf(backendFlowPaths == null ? List.of() : backendFlowPaths);
    }
}
