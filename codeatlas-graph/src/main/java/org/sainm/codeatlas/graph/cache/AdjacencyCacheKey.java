package org.sainm.codeatlas.graph.cache;

public record AdjacencyCacheKey(
    String projectId,
    String snapshotId,
    RelationGroup relationGroup
) {
    public AdjacencyCacheKey {
        projectId = require(projectId, "projectId");
        snapshotId = require(snapshotId, "snapshotId");
        if (relationGroup == null) {
            throw new IllegalArgumentException("relationGroup is required");
        }
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
