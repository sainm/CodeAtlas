package org.sainm.codeatlas.analyzers.source;

public record StrutsForwardInfo(
        String moduleKey,
        String ownerPath,
        String name,
        String path,
        boolean redirect,
        boolean contextRelative,
        SourceLocation location) {
    public StrutsForwardInfo {
        moduleKey = moduleKey == null ? "" : moduleKey;
        ownerPath = ownerPath == null ? "" : ownerPath;
        JavaClassInfo.requireNonBlank(name, "name");
        JavaClassInfo.requireNonBlank(path, "path");
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
