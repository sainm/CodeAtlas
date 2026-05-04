package org.sainm.codeatlas.analyzers.source;

public record SqlTableFactContext(
        String projectId,
        String moduleKey,
        String sourceRootKey,
        String datasourceKey,
        String schemaName,
        String snapshotId,
        String analysisRunId,
        String scopeRunId,
        String scopeKey) {
    public SqlTableFactContext {
        JavaClassInfo.requireNonBlank(projectId, "projectId");
        JavaClassInfo.requireNonBlank(moduleKey, "moduleKey");
        JavaClassInfo.requireNonBlank(sourceRootKey, "sourceRootKey");
        JavaClassInfo.requireNonBlank(datasourceKey, "datasourceKey");
        JavaClassInfo.requireNonBlank(schemaName, "schemaName");
        JavaClassInfo.requireNonBlank(snapshotId, "snapshotId");
        JavaClassInfo.requireNonBlank(analysisRunId, "analysisRunId");
        JavaClassInfo.requireNonBlank(scopeRunId, "scopeRunId");
        JavaClassInfo.requireNonBlank(scopeKey, "scopeKey");
        sourceRootKey = trimSlashes(sourceRootKey);
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
