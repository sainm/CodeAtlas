package org.sainm.codeatlas.analyzers.source;

public record MyBatisFactContext(
        String projectId,
        String moduleKey,
        String javaSourceRootKey,
        String resourceSourceRootKey,
        String snapshotId,
        String analysisRunId,
        String scopeRunId,
        String scopeKey) {
    public MyBatisFactContext {
        requireNonBlank(projectId, "projectId");
        requireNonBlank(moduleKey, "moduleKey");
        requireNonBlank(javaSourceRootKey, "javaSourceRootKey");
        requireNonBlank(resourceSourceRootKey, "resourceSourceRootKey");
        requireNonBlank(snapshotId, "snapshotId");
        requireNonBlank(analysisRunId, "analysisRunId");
        requireNonBlank(scopeRunId, "scopeRunId");
        requireNonBlank(scopeKey, "scopeKey");
        javaSourceRootKey = trimSlashes(javaSourceRootKey);
        resourceSourceRootKey = trimSlashes(resourceSourceRootKey);
        scopeKey = trimSlashes(scopeKey);
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
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
}
