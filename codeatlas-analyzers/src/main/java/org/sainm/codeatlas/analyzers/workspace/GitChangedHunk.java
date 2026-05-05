package org.sainm.codeatlas.analyzers.workspace;

public record GitChangedHunk(
        int oldStartLine,
        int oldLineCount,
        int newStartLine,
        int newLineCount) {
    public GitChangedHunk {
        if (oldStartLine < 0 || oldLineCount < 0 || newStartLine < 0 || newLineCount < 0) {
            throw new IllegalArgumentException("hunk line values must be non-negative");
        }
    }
}
