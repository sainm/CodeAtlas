package org.sainm.codeatlas.analyzers.source;

import java.util.Map;

public record StrutsPluginInfo(
        String moduleKey,
        String className,
        StrutsPluginKind kind,
        Map<String, String> properties,
        SourceLocation location) {
    public StrutsPluginInfo {
        moduleKey = moduleKey == null ? "" : moduleKey;
        JavaClassInfo.requireNonBlank(className, "className");
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        properties = Map.copyOf(properties == null ? Map.of() : properties);
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
