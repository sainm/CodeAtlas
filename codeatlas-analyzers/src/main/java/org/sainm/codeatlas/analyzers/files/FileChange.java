package org.sainm.codeatlas.analyzers.files;

public record FileChange(
    String relativePath,
    FileChangeType type,
    SourceFileFingerprint before,
    SourceFileFingerprint after
) {
    public FileChange {
        relativePath = SourceFileFingerprint.normalize(relativePath);
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
    }
}
