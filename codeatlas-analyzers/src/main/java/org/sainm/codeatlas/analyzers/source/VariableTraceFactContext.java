package org.sainm.codeatlas.analyzers.source;

public record VariableTraceFactContext(
        String projectId,
        String moduleKey,
        String javaSourceRootKey,
        String requestScopeKey,
        String snapshotId,
        String analysisRunId,
        String scopeRunId,
        String scopeKey) {
    public VariableTraceFactContext {
        JavaClassInfo.requireNonBlank(projectId, "projectId");
        JavaClassInfo.requireNonBlank(moduleKey, "moduleKey");
        JavaClassInfo.requireNonBlank(javaSourceRootKey, "javaSourceRootKey");
        JavaClassInfo.requireNonBlank(requestScopeKey, "requestScopeKey");
        JavaClassInfo.requireNonBlank(snapshotId, "snapshotId");
        JavaClassInfo.requireNonBlank(analysisRunId, "analysisRunId");
        JavaClassInfo.requireNonBlank(scopeRunId, "scopeRunId");
        JavaClassInfo.requireNonBlank(scopeKey, "scopeKey");
        javaSourceRootKey = trimSlashes(javaSourceRootKey);
        requestScopeKey = trimSlashes(requestScopeKey);
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
