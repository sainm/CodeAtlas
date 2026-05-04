package org.sainm.codeatlas.analyzers.source;

public record StrutsFormPropertyInfo(
        String name,
        String type,
        String initial,
        SourceLocation location) {
    public StrutsFormPropertyInfo {
        JavaClassInfo.requireNonBlank(name, "name");
        type = type == null ? "" : type;
        initial = initial == null ? "" : initial;
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
