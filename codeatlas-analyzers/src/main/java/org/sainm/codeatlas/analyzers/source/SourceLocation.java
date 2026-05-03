package org.sainm.codeatlas.analyzers.source;

public record SourceLocation(
        String relativePath,
        int line,
        int column) {
    public SourceLocation {
        relativePath = relativePath == null || relativePath.isBlank()
                ? ""
                : relativePath.replace('\\', '/');
        if (line < 0) {
            throw new IllegalArgumentException("line must be non-negative");
        }
        if (column < 0) {
            throw new IllegalArgumentException("column must be non-negative");
        }
    }
}
