package org.sainm.codeatlas.graph.impact;

import java.time.Instant;
import java.util.List;

public record ImpactReport(
    String reportId,
    String projectId,
    String snapshotId,
    String changeSetId,
    ReportDepth depth,
    Instant createdAt,
    List<ImpactPath> paths,
    List<ImpactEvidence> evidenceList,
    boolean truncated
) {
    public ImpactReport {
        reportId = require(reportId, "reportId");
        projectId = require(projectId, "projectId");
        snapshotId = require(snapshotId, "snapshotId");
        changeSetId = require(changeSetId, "changeSetId");
        depth = depth == null ? ReportDepth.FAST : depth;
        createdAt = createdAt == null ? Instant.now() : createdAt;
        paths = List.copyOf(paths);
        evidenceList = List.copyOf(evidenceList);
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
