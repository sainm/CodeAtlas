package org.sainm.codeatlas.analyzers.workspace;

import java.util.List;

public record WorkspaceLayoutProfile(String workspaceId, List<ProjectLayoutCandidate> candidates) {
    public WorkspaceLayoutProfile {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId is required");
        }
        candidates = List.copyOf(candidates == null ? List.of() : candidates);
    }

    public ProjectLayoutCandidate requireCandidate(String rootPath) {
        String normalized = ProjectLayoutCandidate.normalizeRoot(rootPath);
        for (ProjectLayoutCandidate candidate : candidates) {
            if (candidate.rootPath().equals(normalized)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("No project layout candidate for root: " + rootPath);
    }
}
