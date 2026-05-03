package org.sainm.codeatlas.analyzers.source;

public record JavaAnalysisDiagnostic(
        String code,
        String message) {
    public JavaAnalysisDiagnostic {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        message = message == null ? "" : message;
    }
}
