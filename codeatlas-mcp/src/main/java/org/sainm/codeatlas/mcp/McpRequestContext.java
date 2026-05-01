package org.sainm.codeatlas.mcp;

import java.util.Set;

public record McpRequestContext(
    String principal,
    Set<String> allowedProjectIds,
    boolean allProjectsAllowed
) {
    public McpRequestContext {
        principal = principal == null || principal.isBlank() ? "anonymous" : principal.trim();
        allowedProjectIds = allowedProjectIds == null ? Set.of() : Set.copyOf(allowedProjectIds);
    }

    public static McpRequestContext anonymousAllowAll() {
        return allowAllProjects("anonymous");
    }

    public static McpRequestContext allowAllProjects(String principal) {
        return new McpRequestContext(principal, Set.of(), true);
    }

    public static McpRequestContext forProjects(String principal, Set<String> allowedProjectIds) {
        return new McpRequestContext(principal, allowedProjectIds, false);
    }

    public boolean allowsProject(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return true;
        }
        return allProjectsAllowed || allowedProjectIds.contains(projectId);
    }
}
