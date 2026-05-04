package org.sainm.codeatlas.analyzers.source;

public record StrutsModuleInfo(
        String moduleKey,
        String modulePrefix,
        String configPath,
        SourceLocation location) {
    public StrutsModuleInfo {
        moduleKey = moduleKey == null ? "" : moduleKey;
        modulePrefix = modulePrefix == null ? "" : modulePrefix;
        JavaClassInfo.requireNonBlank(configPath, "configPath");
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
