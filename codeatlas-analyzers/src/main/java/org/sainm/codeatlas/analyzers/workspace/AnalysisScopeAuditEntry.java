package org.sainm.codeatlas.analyzers.workspace;

public record AnalysisScopeAuditEntry(
        String scopePath,
        AnalysisScopeDisposition disposition,
        String reason) {
    public AnalysisScopeAuditEntry {
        scopePath = FileInventoryEntry.normalizeRelativePath(scopePath);
        if (disposition == null) {
            throw new IllegalArgumentException("disposition is required");
        }
        reason = reason == null ? "" : reason;
    }
}
