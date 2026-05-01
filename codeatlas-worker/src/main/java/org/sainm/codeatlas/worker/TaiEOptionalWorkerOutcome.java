package org.sainm.codeatlas.worker;

public record TaiEOptionalWorkerOutcome(
    WorkerTaskStatus status,
    int nodeCount,
    int factCount,
    boolean supplemental,
    boolean degraded,
    String message
) {
    public TaiEOptionalWorkerOutcome {
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        nodeCount = Math.max(0, nodeCount);
        factCount = Math.max(0, factCount);
        message = message == null ? "" : message.trim();
    }
}
