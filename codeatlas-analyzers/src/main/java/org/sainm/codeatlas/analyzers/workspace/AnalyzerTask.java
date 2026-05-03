package org.sainm.codeatlas.analyzers.workspace;

public record AnalyzerTask(
        String analyzerId,
        String projectRoot,
        String scopeKey,
        AnalyzerTaskQueue queue,
        AnalyzerTaskReason reason,
        int priority,
        boolean executesProjectCode) {
    public AnalyzerTask {
        if (analyzerId == null || analyzerId.isBlank()) {
            throw new IllegalArgumentException("analyzerId is required");
        }
        projectRoot = ProjectLayoutCandidate.normalizeRoot(projectRoot);
        scopeKey = scopeKey == null || scopeKey.isBlank() ? analyzerId + ":" + projectRoot : scopeKey;
        if (queue == null) {
            throw new IllegalArgumentException("queue is required");
        }
        if (reason == null) {
            throw new IllegalArgumentException("reason is required");
        }
        if (priority < 0) {
            throw new IllegalArgumentException("priority must be non-negative");
        }
    }

    public AnalyzerTask(
            String analyzerId,
            String projectRoot,
            String scopeKey,
            AnalyzerTaskQueue queue,
            AnalyzerTaskReason reason,
            int priority) {
        this(analyzerId, projectRoot, scopeKey, queue, reason, priority, false);
    }
}
