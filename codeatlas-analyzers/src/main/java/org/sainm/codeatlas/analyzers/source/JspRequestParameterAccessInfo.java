package org.sainm.codeatlas.analyzers.source;

public record JspRequestParameterAccessInfo(
        String name,
        JspRequestParameterAccessKind accessKind,
        SourceLocation location) {
    public JspRequestParameterAccessInfo {
        JavaClassInfo.requireNonBlank(name, "name");
        if (accessKind == null) {
            throw new IllegalArgumentException("accessKind is required");
        }
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
