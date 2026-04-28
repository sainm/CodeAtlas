package org.sainm.codeatlas.graph.project;

import java.time.Instant;

public record ScanTaskDescriptor(
    String taskId,
    String projectId,
    AnalysisStatus status,
    Instant createdAt,
    Instant updatedAt,
    String message
) {
    public ScanTaskDescriptor {
        taskId = require(taskId, "taskId");
        projectId = require(projectId, "projectId");
        status = status == null ? AnalysisStatus.PENDING : status;
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
        message = message == null ? "" : message;
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}

