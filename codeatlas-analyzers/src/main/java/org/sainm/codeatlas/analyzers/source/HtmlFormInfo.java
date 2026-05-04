package org.sainm.codeatlas.analyzers.source;

public record HtmlFormInfo(String pagePath, String action, String method, SourceLocation location) {
    public HtmlFormInfo {
        JavaClassInfo.requireNonBlank(pagePath, "pagePath");
        action = action == null ? "" : action;
        method = method == null || method.isBlank() ? "get" : method;
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
