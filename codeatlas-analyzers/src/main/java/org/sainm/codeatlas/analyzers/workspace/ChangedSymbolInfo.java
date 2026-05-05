package org.sainm.codeatlas.analyzers.workspace;

public record ChangedSymbolInfo(
        String kind,
        String identityId,
        String relativePath,
        int line) {
    public ChangedSymbolInfo {
        if (kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("kind is required");
        }
        if (identityId == null || identityId.isBlank()) {
            throw new IllegalArgumentException("identityId is required");
        }
        relativePath = relativePath == null ? "" : FileInventoryEntry.normalizeRelativePath(relativePath);
        if (line < 0) {
            throw new IllegalArgumentException("line must be non-negative");
        }
    }
}
