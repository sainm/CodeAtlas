package org.sainm.codeatlas.graph.project;

import java.nio.file.Path;

public record ModuleDescriptor(
    String projectId,
    String moduleKey,
    Path basePath,
    BuildSystem buildSystem
) {
    public ModuleDescriptor {
        projectId = require(projectId, "projectId");
        moduleKey = moduleKey == null || moduleKey.isBlank() ? "_root" : moduleKey.trim();
        basePath = basePath.toAbsolutePath().normalize();
        buildSystem = buildSystem == null ? BuildSystem.UNKNOWN : buildSystem;
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}

