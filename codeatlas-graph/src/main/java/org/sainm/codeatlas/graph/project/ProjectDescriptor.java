package org.sainm.codeatlas.graph.project;

import java.nio.file.Path;

public record ProjectDescriptor(
    String projectId,
    String projectKey,
    String displayName,
    Path rootPath
) {
    public ProjectDescriptor {
        projectId = require(projectId, "projectId");
        projectKey = require(projectKey, "projectKey");
        displayName = require(displayName, "displayName");
        rootPath = rootPath.toAbsolutePath().normalize();
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}

