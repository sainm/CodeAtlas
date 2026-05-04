package org.sainm.codeatlas.analyzers.source;

public record JspFormInfo(
        String action,
        String method,
        String sourceTag,
        SourceLocation location) {
    public JspFormInfo(String action, String method, SourceLocation location) {
        this(action, method, "form", location);
    }

    public JspFormInfo {
        action = action == null ? "" : action;
        method = method == null || method.isBlank() ? "get" : method;
        sourceTag = sourceTag == null || sourceTag.isBlank() ? "form" : sourceTag;
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
