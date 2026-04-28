package org.sainm.codeatlas.analyzers.index;

import org.sainm.codeatlas.graph.model.SymbolId;
import java.util.List;
import java.util.Objects;

public record ChangedFileCandidates(
    String normalizedPath,
    List<SymbolId> candidates
) {
    public ChangedFileCandidates {
        Objects.requireNonNull(normalizedPath, "normalizedPath");
        Objects.requireNonNull(candidates, "candidates");
        candidates = List.copyOf(candidates);
    }
}
