package org.sainm.codeatlas.analyzers.workspace;

public record AnalyzerTaskExecution(
        AnalyzerTask task,
        AnalyzerTaskStatus status,
        int attempt,
        long timeoutMillis,
        String lastError) {
    public AnalyzerTaskExecution {
        if (task == null) {
            throw new IllegalArgumentException("task is required");
        }
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (attempt < 0) {
            throw new IllegalArgumentException("attempt must be non-negative");
        }
        if (timeoutMillis < 1) {
            throw new IllegalArgumentException("timeoutMillis must be positive");
        }
        lastError = lastError == null ? "" : lastError;
    }

    public String analyzerId() {
        return task.analyzerId();
    }

    public String projectRoot() {
        return task.projectRoot();
    }
}
