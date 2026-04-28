package org.sainm.codeatlas.analyzers.project;

import java.nio.file.Path;

public record SourceRootDescriptor(
    String sourceRootKey,
    Path path
) {
    public SourceRootDescriptor {
        if (sourceRootKey == null || sourceRootKey.isBlank()) {
            throw new IllegalArgumentException("sourceRootKey is required");
        }
        sourceRootKey = sourceRootKey.trim().replace('\\', '/');
        if (path == null) {
            throw new IllegalArgumentException("path is required");
        }
        path = path.toAbsolutePath().normalize();
    }
}
