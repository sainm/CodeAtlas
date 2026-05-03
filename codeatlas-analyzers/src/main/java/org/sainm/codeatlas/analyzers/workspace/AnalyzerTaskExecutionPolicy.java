package org.sainm.codeatlas.analyzers.workspace;

public record AnalyzerTaskExecutionPolicy(
        long timeoutMillis,
        int maxRetries) {
    public AnalyzerTaskExecutionPolicy {
        if (timeoutMillis < 1) {
            throw new IllegalArgumentException("timeoutMillis must be positive");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be non-negative");
        }
    }
}
