package org.sainm.codeatlas.analyzers.source;

public record JavaSourceFactContext(
        String projectId,
        String moduleKey,
        String sourceRootKey,
        String snapshotId,
        String analysisRunId,
        String scopeRunId,
        String scopeKey) {
    public JavaSourceFactContext {
        requireNonBlank(projectId, "projectId");
        requireNonBlank(moduleKey, "moduleKey");
        requireNonBlank(sourceRootKey, "sourceRootKey");
        requireNonBlank(snapshotId, "snapshotId");
        requireNonBlank(analysisRunId, "analysisRunId");
        requireNonBlank(scopeRunId, "scopeRunId");
        requireNonBlank(scopeKey, "scopeKey");
        sourceRootKey = sourceRootKey.replace('\\', '/');
        scopeKey = scopeKey.replace('\\', '/');
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
