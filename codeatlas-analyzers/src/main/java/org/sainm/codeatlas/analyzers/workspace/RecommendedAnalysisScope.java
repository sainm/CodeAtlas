package org.sainm.codeatlas.analyzers.workspace;

public record RecommendedAnalysisScope(
        String projectRoot,
        String analyzerId,
        String reason) {
    public RecommendedAnalysisScope {
        projectRoot = ProjectLayoutCandidate.normalizeRoot(projectRoot);
        if (analyzerId == null || analyzerId.isBlank()) {
            throw new IllegalArgumentException("analyzerId is required");
        }
        reason = reason == null ? "" : reason;
    }
}
