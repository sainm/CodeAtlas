package org.sainm.codeatlas.analyzers.workspace;

public enum AnalyzerTaskStatus {
    PLANNED,
    RUNNING,
    RETRY_WAITING,
    SUCCEEDED,
    FAILED,
    TIMED_OUT,
    CANCELLED
}
