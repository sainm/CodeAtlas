package org.sainm.codeatlas.analyzers.workspace;

import java.util.List;

public record ProjectReviewCandidate(
        String rootPath,
        ProjectLayoutType layoutType,
        ProjectReviewStatus status,
        List<String> evidencePaths,
        List<String> entrypointEvidencePaths,
        List<String> boundaryEvidencePaths) {
    public ProjectReviewCandidate {
        rootPath = ProjectLayoutCandidate.normalizeRoot(rootPath);
        if (layoutType == null) {
            throw new IllegalArgumentException("layoutType is required");
        }
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        evidencePaths = copyNormalized(evidencePaths);
        entrypointEvidencePaths = copyNormalized(entrypointEvidencePaths);
        boundaryEvidencePaths = copyNormalized(boundaryEvidencePaths);
    }

    private static List<String> copyNormalized(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return List.of();
        }
        return paths.stream()
                .map(FileInventoryEntry::normalizeRelativePath)
                .distinct()
                .sorted()
                .toList();
    }
}
