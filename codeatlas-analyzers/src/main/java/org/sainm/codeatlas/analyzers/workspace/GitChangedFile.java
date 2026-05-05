package org.sainm.codeatlas.analyzers.workspace;

import java.util.List;

public record GitChangedFile(
        String oldPath,
        String newPath,
        String changeType,
        List<GitChangedHunk> hunks) {
    public GitChangedFile {
        oldPath = normalizeOptionalPath(oldPath);
        newPath = normalizeOptionalPath(newPath);
        if (oldPath.isBlank() && newPath.isBlank()) {
            throw new IllegalArgumentException("oldPath or newPath is required");
        }
        if (changeType == null || changeType.isBlank()) {
            throw new IllegalArgumentException("changeType is required");
        }
        hunks = List.copyOf(hunks == null ? List.of() : hunks);
    }

    public String effectivePath() {
        return newPath.isBlank() ? oldPath : newPath;
    }

    private static String normalizeOptionalPath(String path) {
        return path == null || path.isBlank() ? "" : FileInventoryEntry.normalizeRelativePath(path);
    }
}
