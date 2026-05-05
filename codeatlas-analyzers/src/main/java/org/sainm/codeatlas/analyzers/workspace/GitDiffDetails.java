package org.sainm.codeatlas.analyzers.workspace;

import java.util.List;

public record GitDiffDetails(
        String branchName,
        String baseCommitId,
        String headCommitId,
        List<GitChangedFile> changedFiles) {
    public GitDiffDetails {
        branchName = branchName == null ? "" : branchName;
        baseCommitId = baseCommitId == null ? "" : baseCommitId;
        headCommitId = headCommitId == null ? "" : headCommitId;
        changedFiles = List.copyOf(changedFiles == null ? List.of() : changedFiles);
    }

    public GitDiffSummary summary() {
        return new GitDiffSummary(changedFiles.stream()
                .map(GitChangedFile::effectivePath)
                .toList());
    }
}
