package org.sainm.codeatlas.analyzers.git;

public record GitRepositoryInfo(
    String branch,
    String headCommit
) {
    public GitRepositoryInfo {
        branch = branch == null || branch.isBlank() ? "UNKNOWN" : branch.trim();
        headCommit = headCommit == null || headCommit.isBlank() ? "UNKNOWN" : headCommit.trim();
    }
}
