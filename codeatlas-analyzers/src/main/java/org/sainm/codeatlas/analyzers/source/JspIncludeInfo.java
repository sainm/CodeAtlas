package org.sainm.codeatlas.analyzers.source;

public record JspIncludeInfo(
        String path,
        JspIncludeKind kind,
        SourceLocation location) {
    public JspIncludeInfo {
        JavaClassInfo.requireNonBlank(path, "path");
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
