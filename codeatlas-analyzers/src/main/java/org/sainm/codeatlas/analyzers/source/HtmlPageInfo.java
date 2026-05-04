package org.sainm.codeatlas.analyzers.source;

public record HtmlPageInfo(String path, SourceLocation location) {
    public HtmlPageInfo {
        JavaClassInfo.requireNonBlank(path, "path");
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
