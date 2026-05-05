package org.sainm.codeatlas.analyzers.source;

import java.util.List;

public record SqlTableAnalysisResult(
        List<SqlTableAccessInfo> tableAccesses,
        List<SqlColumnAccessInfo> columnAccesses,
        List<JavaAnalysisDiagnostic> diagnostics) {
    public SqlTableAnalysisResult(
            List<SqlTableAccessInfo> tableAccesses,
            List<JavaAnalysisDiagnostic> diagnostics) {
        this(tableAccesses, List.of(), diagnostics);
    }

    public SqlTableAnalysisResult {
        tableAccesses = tableAccesses == null ? List.of() : List.copyOf(tableAccesses);
        columnAccesses = columnAccesses == null ? List.of() : List.copyOf(columnAccesses);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
