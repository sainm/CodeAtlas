package org.sainm.codeatlas.analyzers.source;

public record JavaSourceCacheEntry(
        String relativePath,
        String sha256,
        JavaSourceAnalysisResult result) {
    public JavaSourceCacheEntry {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("relativePath is required");
        }
        relativePath = relativePath.replace('\\', '/');
        if (sha256 == null || sha256.isBlank()) {
            throw new IllegalArgumentException("sha256 is required");
        }
        if (result == null) {
            throw new IllegalArgumentException("result is required");
        }
    }
}
