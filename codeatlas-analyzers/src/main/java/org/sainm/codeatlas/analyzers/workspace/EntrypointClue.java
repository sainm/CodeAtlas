package org.sainm.codeatlas.analyzers.workspace;

public record EntrypointClue(
        EntrypointKind kind,
        String evidencePath,
        String target,
        String source) {
    public EntrypointClue {
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        evidencePath = FileInventoryEntry.normalizeRelativePath(evidencePath);
        target = target == null ? "" : target;
        source = source == null ? "" : source;
    }
}
