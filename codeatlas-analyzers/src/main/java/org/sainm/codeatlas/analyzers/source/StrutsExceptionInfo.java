package org.sainm.codeatlas.analyzers.source;

public record StrutsExceptionInfo(
        String moduleKey,
        String ownerPath,
        String key,
        String type,
        String path,
        SourceLocation location) {
    public StrutsExceptionInfo {
        moduleKey = moduleKey == null ? "" : moduleKey;
        ownerPath = ownerPath == null ? "" : ownerPath;
        key = key == null ? "" : key;
        JavaClassInfo.requireNonBlank(type, "type");
        path = path == null ? "" : path;
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
