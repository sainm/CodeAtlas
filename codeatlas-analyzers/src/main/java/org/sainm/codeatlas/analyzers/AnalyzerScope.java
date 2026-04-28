package org.sainm.codeatlas.analyzers;

import java.nio.file.Path;

public record AnalyzerScope(
    String projectId,
    String moduleKey,
    String snapshotId,
    String analysisRunId,
    String scopeKey,
    Path root
) {
}

