package org.sainm.codeatlas.server;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public record ProjectAccessPolicy(
    Set<String> allowedProjectIds,
    boolean allProjectsAllowed
) {
    public ProjectAccessPolicy {
        allowedProjectIds = allowedProjectIds == null ? Set.of() : Set.copyOf(allowedProjectIds);
    }

    public static ProjectAccessPolicy allowAll() {
        return new ProjectAccessPolicy(Set.of(), true);
    }

    public static ProjectAccessPolicy allowOnly(String... projectIds) {
        return new ProjectAccessPolicy(
            Arrays.stream(projectIds == null ? new String[0] : projectIds)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(Collectors.toUnmodifiableSet()),
            false
        );
    }

    public void requireAllowed(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return;
        }
        String normalized = projectId.trim();
        if (!allProjectsAllowed && !allowedProjectIds.contains(normalized)) {
            throw new ProjectAccessDeniedException(normalized);
        }
    }
}
