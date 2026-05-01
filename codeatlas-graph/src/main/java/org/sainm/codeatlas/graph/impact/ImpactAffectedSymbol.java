package org.sainm.codeatlas.graph.impact;

import org.sainm.codeatlas.graph.model.SymbolId;

public record ImpactAffectedSymbol(
    String category,
    SymbolId symbolId,
    String displayName
) {
    public ImpactAffectedSymbol {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("category is required");
        }
        if (symbolId == null) {
            throw new IllegalArgumentException("symbolId is required");
        }
        category = category.trim();
        displayName = displayName == null || displayName.isBlank() ? symbolId.value() : displayName.trim();
    }
}
