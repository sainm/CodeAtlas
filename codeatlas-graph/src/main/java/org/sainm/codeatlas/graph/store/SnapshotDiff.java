package org.sainm.codeatlas.graph.store;

import org.sainm.codeatlas.graph.model.FactKey;
import java.util.Set;

public record SnapshotDiff(
    Set<FactKey> added,
    Set<FactKey> removed,
    Set<FactKey> retained
) {
    public SnapshotDiff {
        added = Set.copyOf(added);
        removed = Set.copyOf(removed);
        retained = Set.copyOf(retained);
    }
}

