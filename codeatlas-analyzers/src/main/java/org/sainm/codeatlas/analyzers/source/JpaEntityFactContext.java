package org.sainm.codeatlas.analyzers.source;

public record JpaEntityFactContext(
        String projectId,
        String moduleKey,
        String javaSourceRootKey,
        String datasourceKey,
        String schemaName,
        String snapshotId,
        String analysisRunId,
        String scopeRunId,
        String scopeKey) {
    public JpaEntityFactContext {
        JavaClassInfo.requireNonBlank(projectId, "projectId");
        JavaClassInfo.requireNonBlank(moduleKey, "moduleKey");
        JavaClassInfo.requireNonBlank(javaSourceRootKey, "javaSourceRootKey");
        JavaClassInfo.requireNonBlank(datasourceKey, "datasourceKey");
        JavaClassInfo.requireNonBlank(schemaName, "schemaName");
        JavaClassInfo.requireNonBlank(snapshotId, "snapshotId");
        JavaClassInfo.requireNonBlank(analysisRunId, "analysisRunId");
        JavaClassInfo.requireNonBlank(scopeRunId, "scopeRunId");
        JavaClassInfo.requireNonBlank(scopeKey, "scopeKey");
        javaSourceRootKey = trimSlashes(javaSourceRootKey);
        scopeKey = trimSlashes(scopeKey);
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
