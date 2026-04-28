package org.sainm.codeatlas.worker;

import java.nio.file.Path;

public record AnalysisWorkerJob(
    String projectId,
    String projectKey,
    String moduleKey,
    String snapshotId,
    String analysisRunId,
    String scopeKey,
    Path root
) {
    public AnalysisWorkerJob {
        projectId = require(projectId, "projectId");
        projectKey = require(projectKey, "projectKey");
        moduleKey = moduleKey == null || moduleKey.isBlank() ? "_root" : moduleKey.trim();
        snapshotId = require(snapshotId, "snapshotId");
        analysisRunId = require(analysisRunId, "analysisRunId");
        scopeKey = scopeKey == null || scopeKey.isBlank() ? "project" : scopeKey.trim();
        if (root == null) {
            throw new IllegalArgumentException("root is required");
        }
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
