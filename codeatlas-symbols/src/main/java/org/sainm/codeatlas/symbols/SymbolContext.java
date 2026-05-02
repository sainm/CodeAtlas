package org.sainm.codeatlas.symbols;

public record SymbolContext(String projectKey, String moduleKey, String workspaceRoot) {
    public SymbolContext {
        if (projectKey == null || projectKey.isBlank()) {
            throw new IllegalArgumentException("projectKey is required");
        }
        if (moduleKey == null || moduleKey.isBlank()) {
            throw new IllegalArgumentException("moduleKey is required");
        }
        if (workspaceRoot == null || workspaceRoot.isBlank()) {
            throw new IllegalArgumentException("workspaceRoot is required");
        }
    }

    public static SymbolContext of(String projectKey, String moduleKey, String workspaceRoot) {
        return new SymbolContext(projectKey, moduleKey, workspaceRoot);
    }
}
