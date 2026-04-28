package org.sainm.codeatlas.graph.project;

import java.time.Instant;

public record AnalysisRunDescriptor(
    String projectId,
    String snapshotId,
    String analysisRunId,
    AnalysisStatus status,
    Instant startedAt,
    String message
) {
    public AnalysisRunDescriptor {
        projectId = require(projectId, "projectId");
        snapshotId = require(snapshotId, "snapshotId");
        analysisRunId = require(analysisRunId, "analysisRunId");
        status = status == null ? AnalysisStatus.PENDING : status;
        startedAt = startedAt == null ? Instant.now() : startedAt;
        message = message == null ? "" : message;
    }

    public AnalysisRunDescriptor withStatus(AnalysisStatus newStatus, String newMessage) {
        return new AnalysisRunDescriptor(projectId, snapshotId, analysisRunId, newStatus, startedAt, newMessage);
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}

