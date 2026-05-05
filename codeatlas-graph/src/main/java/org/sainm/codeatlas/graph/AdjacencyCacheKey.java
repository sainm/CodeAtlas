package org.sainm.codeatlas.graph;

import org.sainm.codeatlas.facts.RelationFamily;

/**
 * Key for JVM adjacency cache lookups.
 *
 * <p>Includes an optional {@link ClasspathCacheScope} to support
 * JAR/module-level cache granularity. When no scope is specified,
 * the default project-wide scope is used.
 */
public record AdjacencyCacheKey(
        String projectId,
        String snapshotId,
        RelationFamily relationFamily,
        ClasspathCacheScope classpathScope) {
    public AdjacencyCacheKey {
        requireNonBlank(projectId, "projectId");
        requireNonBlank(snapshotId, "snapshotId");
        if (relationFamily == null) {
            throw new IllegalArgumentException("relationFamily is required");
        }
        classpathScope = classpathScope == null
                ? ClasspathCacheScope.projectWide() : classpathScope;
    }

    public AdjacencyCacheKey(String projectId, String snapshotId, RelationFamily relationFamily) {
        this(projectId, snapshotId, relationFamily, ClasspathCacheScope.projectWide());
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
