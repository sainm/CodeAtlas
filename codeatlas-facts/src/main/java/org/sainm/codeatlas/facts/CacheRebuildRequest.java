package org.sainm.codeatlas.facts;

import java.util.List;

public record CacheRebuildRequest(
        String projectId,
        String snapshotId,
        List<RelationFamily> relationFamilies,
        int activeFactCount) {
    public CacheRebuildRequest {
        requireNonBlank(projectId, "projectId");
        requireNonBlank(snapshotId, "snapshotId");
        if (relationFamilies == null || relationFamilies.isEmpty()) {
            throw new IllegalArgumentException("relationFamilies are required");
        }
        relationFamilies = List.copyOf(relationFamilies);
        for (RelationFamily relationFamily : relationFamilies) {
            if (relationFamily == null) {
                throw new IllegalArgumentException("relationFamilies cannot contain null items");
            }
        }
        if (activeFactCount < 0) {
            throw new IllegalArgumentException("activeFactCount must be non-negative");
        }
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
