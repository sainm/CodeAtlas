package org.sainm.codeatlas.analyzers.workspace;

public record ImportReviewOverview(
        String workspaceId,
        ImportSourceType sourceType,
        ImportMode mode,
        int fileCount,
        long totalSizeBytes,
        int projectCandidateCount) {
    public ImportReviewOverview {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId is required");
        }
        if (sourceType == null) {
            throw new IllegalArgumentException("sourceType is required");
        }
        if (mode == null) {
            throw new IllegalArgumentException("mode is required");
        }
        if (fileCount < 0) {
            throw new IllegalArgumentException("fileCount must be non-negative");
        }
        if (totalSizeBytes < 0) {
            throw new IllegalArgumentException("totalSizeBytes must be non-negative");
        }
        if (projectCandidateCount < 0) {
            throw new IllegalArgumentException("projectCandidateCount must be non-negative");
        }
    }
}
