package org.sainm.codeatlas.analyzers.workspace;

import java.util.List;

public record GitDiffSummary(List<String> changedPaths) {
    public GitDiffSummary {
        if (changedPaths == null || changedPaths.isEmpty()) {
            changedPaths = List.of();
        } else {
            changedPaths = changedPaths.stream()
                    .map(FileInventoryEntry::normalizeRelativePath)
                    .distinct()
                    .sorted()
                    .toList();
        }
    }
}
