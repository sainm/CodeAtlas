package org.sainm.codeatlas.analyzers.source;

import java.util.List;

public record JpaEntityAnalysisResult(
        List<JpaEntityInfo> entities,
        List<JavaAnalysisDiagnostic> diagnostics) {
    public JpaEntityAnalysisResult {
        entities = List.copyOf(entities == null ? List.of() : entities);
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }
}
