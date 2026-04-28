package org.sainm.codeatlas.analyzers.files;

import java.nio.file.Path;

public record SourceFileFingerprint(
    Path root,
    Path path,
    String relativePath,
    String normalizedPathLower,
    long size,
    String sha256
) {
    public SourceFileFingerprint {
        root = root.toAbsolutePath().normalize();
        path = path.toAbsolutePath().normalize();
        relativePath = normalize(relativePath);
        normalizedPathLower = normalizedPathLower == null ? relativePath.toLowerCase() : normalizedPathLower;
        if (size < 0) {
            throw new IllegalArgumentException("size must be non-negative");
        }
        if (sha256 == null || sha256.isBlank()) {
            throw new IllegalArgumentException("sha256 is required");
        }
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("relativePath is required");
        }
        String normalized = value.trim().replace('\\', '/');
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        return normalized.startsWith("/") ? normalized.substring(1) : normalized;
    }
}

