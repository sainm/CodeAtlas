package org.sainm.codeatlas.analyzers.source;

public record JspForwardInfo(
        String path,
        SourceLocation location) {
    public JspForwardInfo {
        JavaClassInfo.requireNonBlank(path, "path");
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
