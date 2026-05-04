package org.sainm.codeatlas.analyzers.source;

import java.util.List;

public record StrutsConfigFactContext(
        String projectId,
        String moduleKey,
        String configSourceRootKey,
        String webSourceRootKey,
        String javaSourceRootKey,
        String snapshotId,
        String analysisRunId,
        String scopeRunId,
        String scopeKey) {
    public StrutsConfigFactContext {
        requireNonBlank(projectId, "projectId");
        requireNonBlank(moduleKey, "moduleKey");
        requireNonBlank(configSourceRootKey, "configSourceRootKey");
        requireNonBlank(webSourceRootKey, "webSourceRootKey");
        requireNonBlank(javaSourceRootKey, "javaSourceRootKey");
        requireNonBlank(snapshotId, "snapshotId");
        requireNonBlank(analysisRunId, "analysisRunId");
        requireNonBlank(scopeRunId, "scopeRunId");
        requireNonBlank(scopeKey, "scopeKey");
        configSourceRootKey = configSourceRootKey.replace('\\', '/');
        webSourceRootKey = webSourceRootKey.replace('\\', '/');
        javaSourceRootKey = javaSourceRootKey.replace('\\', '/');
        scopeKey = scopeKey.replace('\\', '/');
    }

    List<String> identitySourceRoots() {
        return List.of(configSourceRootKey, webSourceRootKey, javaSourceRootKey);
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
