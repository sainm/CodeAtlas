package org.sainm.codeatlas.server;

public record ApiEnvelope<T>(
        String snapshotId,
        T data) {
    public ApiEnvelope {
        snapshotId = snapshotId == null || snapshotId.isBlank() ? "latest-committed" : snapshotId;
    }
}
