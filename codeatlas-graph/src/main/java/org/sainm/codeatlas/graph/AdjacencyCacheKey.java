package org.sainm.codeatlas.graph;

import org.sainm.codeatlas.facts.RelationFamily;

public record AdjacencyCacheKey(
        String projectId,
        String snapshotId,
        RelationFamily relationFamily) {
    public AdjacencyCacheKey {
        requireNonBlank(projectId, "projectId");
        requireNonBlank(snapshotId, "snapshotId");
        if (relationFamily == null) {
            throw new IllegalArgumentException("relationFamily is required");
        }
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
