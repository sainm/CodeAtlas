package org.sainm.codeatlas.analyzers.workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public record ImportRequest(
        String workspaceId,
        ImportSourceType sourceType,
        Path sourceRoot,
        Optional<Path> archivePath,
        ImportMode mode) {
    public ImportRequest {
        requireNonBlank(workspaceId, "workspaceId");
        if (sourceType == null) {
            throw new IllegalArgumentException("sourceType is required");
        }
        if (sourceRoot == null || !Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("sourceRoot must be an existing directory");
        }
        archivePath = archivePath == null ? Optional.empty() : archivePath;
        if (sourceType == ImportSourceType.UPLOADED_ARCHIVE
                && (archivePath.isEmpty() || !Files.isRegularFile(archivePath.orElseThrow()))) {
            throw new IllegalArgumentException("uploaded archive source requires an archive file");
        }
        if (sourceType == ImportSourceType.LOCAL_FOLDER && archivePath.isPresent()) {
            throw new IllegalArgumentException("local folder source cannot have an archive path");
        }
        if (mode == null) {
            throw new IllegalArgumentException("mode is required");
        }
    }

    public static ImportRequest localFolder(String workspaceId, Path sourceRoot, ImportMode mode) {
        return new ImportRequest(workspaceId, ImportSourceType.LOCAL_FOLDER, sourceRoot, Optional.empty(), mode);
    }

    public static ImportRequest uploadedArchive(String workspaceId, Path archivePath, Path extractedRoot, ImportMode mode) {
        return new ImportRequest(workspaceId, ImportSourceType.UPLOADED_ARCHIVE, extractedRoot, Optional.of(archivePath), mode);
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
