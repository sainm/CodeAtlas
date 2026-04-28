package org.sainm.codeatlas.graph.project;

import java.time.Instant;
import java.util.Optional;

public record SnapshotDescriptor(
    String projectId,
    String snapshotId,
    Optional<String> parentSnapshotId,
    Instant createdAt
) {
    public SnapshotDescriptor {
        projectId = require(projectId, "projectId");
        snapshotId = require(snapshotId, "snapshotId");
        parentSnapshotId = parentSnapshotId == null ? Optional.empty() : parentSnapshotId.map(String::trim).filter(value -> !value.isBlank());
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}

