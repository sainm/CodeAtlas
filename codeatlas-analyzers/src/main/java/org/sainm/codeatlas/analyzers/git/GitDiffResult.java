package org.sainm.codeatlas.analyzers.git;

import java.util.List;

public record GitDiffResult(
    String oldCommit,
    String newCommit,
    List<ChangedFile> changedFiles,
    String unifiedDiff
) {
    public GitDiffResult {
        oldCommit = require(oldCommit, "oldCommit");
        newCommit = require(newCommit, "newCommit");
        changedFiles = List.copyOf(changedFiles);
        unifiedDiff = unifiedDiff == null ? "" : unifiedDiff;
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
