package org.sainm.codeatlas.analyzers.workspace;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public record WorkspaceInventory(
        String workspaceId,
        ImportSourceType sourceType,
        ImportMode mode,
        Path sourceRoot,
        Optional<Path> archivePath,
        List<FileInventoryEntry> entries) {
    public WorkspaceInventory {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId is required");
        }
        if (sourceType == null) {
            throw new IllegalArgumentException("sourceType is required");
        }
        if (mode == null) {
            throw new IllegalArgumentException("mode is required");
        }
        if (sourceRoot == null) {
            throw new IllegalArgumentException("sourceRoot is required");
        }
        archivePath = archivePath == null ? Optional.empty() : archivePath;
        entries = List.copyOf(entries == null ? List.of() : entries);
    }

    public FileInventoryEntry requireEntry(String relativePath) {
        String normalized = FileInventoryEntry.normalizeRelativePath(relativePath);
        for (FileInventoryEntry entry : entries) {
            if (entry.relativePath().equals(normalized)) {
                return entry;
            }
        }
        throw new IllegalArgumentException("No inventory entry for path: " + relativePath);
    }
}
