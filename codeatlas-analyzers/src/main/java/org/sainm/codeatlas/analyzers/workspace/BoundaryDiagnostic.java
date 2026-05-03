package org.sainm.codeatlas.analyzers.workspace;

public record BoundaryDiagnostic(
        AnalysisBoundary boundary,
        String evidencePath,
        String symbol,
        String message) {
    public BoundaryDiagnostic {
        if (boundary == null) {
            throw new IllegalArgumentException("boundary is required");
        }
        evidencePath = FileInventoryEntry.normalizeRelativePath(evidencePath);
        symbol = symbol == null ? "" : symbol;
        message = message == null ? "" : message;
    }
}
