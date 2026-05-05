package org.sainm.codeatlas.analyzers.workspace;

import java.util.List;

import org.sainm.codeatlas.analyzers.source.JavaAnalysisDiagnostic;

public record ChangedSymbolResolution(
        List<ChangedSymbolInfo> symbols,
        List<JavaAnalysisDiagnostic> diagnostics) {
    public ChangedSymbolResolution {
        symbols = List.copyOf(symbols == null ? List.of() : symbols);
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }
}
