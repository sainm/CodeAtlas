package org.sainm.codeatlas.analyzers.files;

import java.util.List;

public record IncrementalScanDiff(
    List<FileChange> changes
) {
    public IncrementalScanDiff {
        changes = List.copyOf(changes);
    }

    public List<FileChange> changedFiles() {
        return changes.stream()
            .filter(change -> change.type() != FileChangeType.UNCHANGED)
            .toList();
    }

    public boolean hasChanges() {
        return changes.stream().anyMatch(change -> change.type() != FileChangeType.UNCHANGED);
    }
}
