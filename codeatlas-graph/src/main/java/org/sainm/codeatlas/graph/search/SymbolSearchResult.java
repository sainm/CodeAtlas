package org.sainm.codeatlas.graph.search;

import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.model.SymbolKind;

public record SymbolSearchResult(
    SymbolId symbolId,
    SymbolKind kind,
    String displayName,
    int score
) {
    public SymbolSearchResult {
        if (symbolId == null) {
            throw new IllegalArgumentException("symbolId is required");
        }
        kind = symbolId.kind();
        displayName = displayName == null || displayName.isBlank() ? symbolId.value() : displayName.trim();
    }
}
