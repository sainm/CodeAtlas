package org.sainm.codeatlas.analyzers.workspace;

public record FileInventoryEntry(
        String relativePath,
        long sizeBytes,
        String sha256,
        FileCapabilityLevel level,
        DecodeDiagnostic decodeDiagnostic) {
    public FileInventoryEntry {
        relativePath = normalizeRelativePath(relativePath);
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes cannot be negative");
        }
        if (level == null) {
            throw new IllegalArgumentException("level is required");
        }
        if (level == FileCapabilityLevel.L5_SKIPPED && sha256 == null) {
            sha256 = "";
        }
        if (level == FileCapabilityLevel.L5_SKIPPED && sha256.isBlank()) {
            sha256 = "";
        } else {
            requireSha256(sha256);
        }
        if (decodeDiagnostic == null) {
            throw new IllegalArgumentException("decodeDiagnostic is required");
        }
    }

    static String normalizeRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("relativePath is required");
        }
        String normalized = relativePath.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.endsWith("/") || normalized.contains("//") || hasTraversalSegment(normalized)) {
            throw new IllegalArgumentException("relativePath must be normalized and relative");
        }
        return normalized;
    }

    private static boolean hasTraversalSegment(String path) {
        for (String segment : path.split("/")) {
            if (segment.equals("..")) {
                return true;
            }
        }
        return false;
    }

    private static void requireSha256(String sha256) {
        if (sha256 == null || sha256.length() != 64) {
            throw new IllegalArgumentException("sha256 must be 64 hex characters");
        }
        for (int i = 0; i < sha256.length(); i++) {
            char c = sha256.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                throw new IllegalArgumentException("sha256 must be lowercase hex");
            }
        }
    }
}
