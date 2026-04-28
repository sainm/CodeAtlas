package org.sainm.codeatlas.analyzers.project;

import org.sainm.codeatlas.graph.project.BuildSystem;
import java.nio.file.Path;
import java.util.List;

public record DiscoveredModule(
    String moduleKey,
    Path basePath,
    BuildSystem buildSystem,
    List<SourceRootDescriptor> sourceRoots
) {
    public DiscoveredModule {
        moduleKey = moduleKey == null || moduleKey.isBlank() ? "_root" : moduleKey.trim();
        if (basePath == null) {
            throw new IllegalArgumentException("basePath is required");
        }
        basePath = basePath.toAbsolutePath().normalize();
        buildSystem = buildSystem == null ? BuildSystem.UNKNOWN : buildSystem;
        sourceRoots = List.copyOf(sourceRoots);
    }
}
