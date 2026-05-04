package org.sainm.codeatlas.analyzers.source;

public record JspTaglibInfo(
        String prefix,
        String uri,
        SourceLocation location) {
    public JspTaglibInfo {
        JavaClassInfo.requireNonBlank(prefix, "prefix");
        JavaClassInfo.requireNonBlank(uri, "uri");
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
