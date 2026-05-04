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
        javaSourceRootKey = javaSourceRootKey.replace('\\', '/');
        resourceSourceRootKey = resourceSourceRootKey.replace('\\', '/');
        scopeKey = scopeKey.replace('\\', '/');
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
