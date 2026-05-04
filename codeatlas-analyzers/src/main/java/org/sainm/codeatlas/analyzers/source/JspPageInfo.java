package org.sainm.codeatlas.analyzers.source;

public record JspPageInfo(String path, SourceLocation location) {
    public JspPageInfo {
        JavaClassInfo.requireNonBlank(path, "path");
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
