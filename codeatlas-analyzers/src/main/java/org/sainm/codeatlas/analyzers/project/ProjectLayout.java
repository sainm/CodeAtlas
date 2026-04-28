package org.sainm.codeatlas.analyzers.project;

import java.util.List;

public record ProjectLayout(
    List<DiscoveredModule> modules
) {
    public ProjectLayout {
        modules = List.copyOf(modules);
    }
}
