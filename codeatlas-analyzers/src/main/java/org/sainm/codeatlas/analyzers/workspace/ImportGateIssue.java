package org.sainm.codeatlas.analyzers.workspace;

public record ImportGateIssue(
        ImportGateSeverity severity,
        String code,
        String evidencePath,
        String message) {
    public ImportGateIssue {
        if (severity == null) {
            throw new IllegalArgumentException("severity is required");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        evidencePath = evidencePath == null || evidencePath.isBlank()
                ? ""
                : FileInventoryEntry.normalizeRelativePath(evidencePath);
        message = message == null ? "" : message;
    }

    public static ImportGateIssue blocking(String code, String evidencePath, String message) {
        return new ImportGateIssue(ImportGateSeverity.BLOCKING, code, evidencePath, message);
    }

    public static ImportGateIssue warning(String code, String evidencePath, String message) {
        return new ImportGateIssue(ImportGateSeverity.WARNING, code, evidencePath, message);
    }
}
