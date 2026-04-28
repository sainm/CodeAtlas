package org.sainm.codeatlas.analyzers.git;

public record ChangedFile(
    String oldPath,
    String newPath
) {
    public ChangedFile {
        oldPath = blankToNull(oldPath);
        newPath = blankToNull(newPath);
        if (oldPath == null && newPath == null) {
            throw new IllegalArgumentException("at least one path is required");
        }
    }

    public String effectivePath() {
        return newPath == null ? oldPath : newPath;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank() || value.equals("/dev/null")) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("a/") || trimmed.startsWith("b/")) {
            return trimmed.substring(2);
        }
        return trimmed;
    }
}
