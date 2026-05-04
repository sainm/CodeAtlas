package org.sainm.codeatlas.analyzers.source;

public record StrutsMessageResourceInfo(
        String moduleKey,
        String parameter,
        String key,
        SourceLocation location) {
    public StrutsMessageResourceInfo {
        moduleKey = moduleKey == null ? "" : moduleKey;
        JavaClassInfo.requireNonBlank(parameter, "parameter");
        key = key == null ? "" : key;
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
