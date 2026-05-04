package org.sainm.codeatlas.analyzers.source;

public record ScriptResourceInfo(String pagePath, String src, SourceLocation location) {
    public ScriptResourceInfo {
        JavaClassInfo.requireNonBlank(pagePath, "pagePath");
        JavaClassInfo.requireNonBlank(src, "src");
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
