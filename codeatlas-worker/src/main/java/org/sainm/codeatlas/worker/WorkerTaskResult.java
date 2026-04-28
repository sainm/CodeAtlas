package org.sainm.codeatlas.worker;

import java.time.Duration;

public record WorkerTaskResult<T>(
    WorkerTaskStatus status,
    T value,
    String errorMessage,
    int attempts,
    Duration elapsed
) {
    public WorkerTaskResult {
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        errorMessage = errorMessage == null ? "" : errorMessage.trim();
        elapsed = elapsed == null ? Duration.ZERO : elapsed;
    }
}
