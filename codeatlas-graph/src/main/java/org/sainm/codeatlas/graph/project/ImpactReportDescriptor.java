package org.sainm.codeatlas.graph.project;

import java.time.Instant;

public record ImpactReportDescriptor(
    String reportId,
    String projectId,
    String snapshotId,
    String changeSetId,
    AnalysisStatus status,
    Instant createdAt
) {
    public ImpactReportDescriptor {
        reportId = require(reportId, "reportId");
        projectId = require(projectId, "projectId");
        snapshotId = require(snapshotId, "snapshotId");
        changeSetId = require(changeSetId, "changeSetId");
        status = status == null ? AnalysisStatus.PENDING : status;
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}

