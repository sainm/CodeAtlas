package org.sainm.codeatlas.analyzers.source;

import java.util.List;

public record SqlTableAnalysisResult(
        List<SqlTableAccessInfo> tableAccesses,
        List<JavaAnalysisDiagnostic> diagnostics) {
    public SqlTableAnalysisResult {
        tableAccesses = tableAccesses == null ? List.of() : List.copyOf(tableAccesses);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
