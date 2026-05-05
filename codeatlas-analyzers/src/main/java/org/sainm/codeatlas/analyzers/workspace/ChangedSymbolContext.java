package org.sainm.codeatlas.analyzers.workspace;

public record ChangedSymbolContext(
        String projectId,
        String moduleKey,
        String javaSourceRootKey,
        String resourceSourceRootKey,
        String webSourceRootKey,
        String datasourceKey,
        String schemaName) {
    public ChangedSymbolContext {
        requireNonBlank(projectId, "projectId");
        requireNonBlank(moduleKey, "moduleKey");
        javaSourceRootKey = trimSlashes(defaultIfBlank(javaSourceRootKey, "src/main/java"));
        resourceSourceRootKey = trimSlashes(defaultIfBlank(resourceSourceRootKey, "src/main/resources"));
        webSourceRootKey = trimSlashes(defaultIfBlank(webSourceRootKey, "src/main/webapp"));
        datasourceKey = defaultIfBlank(datasourceKey, "default");
        schemaName = defaultIfBlank(schemaName, "public");
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String trimSlashes(String value) {
        String normalized = value.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
