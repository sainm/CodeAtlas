package org.sainm.codeatlas.server;

import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.model.SymbolKind;

public record BusinessQueryCandidate(
    SymbolId symbolId,
    SymbolKind kind,
    String displayName,
    int score,
    String suggestedParameter,
    String reason
) {
    public BusinessQueryCandidate {
        if (symbolId == null) {
            throw new IllegalArgumentException("symbolId is required");
        }
        kind = symbolId.kind();
        displayName = displayName == null || displayName.isBlank() ? displayName(symbolId) : displayName.trim();
        suggestedParameter = suggestedParameter == null || suggestedParameter.isBlank() ? "symbolId" : suggestedParameter.trim();
        reason = reason == null ? "" : reason.trim();
    }

    private static String displayName(SymbolId symbolId) {
        if (symbolId.memberName() != null) {
            return symbolId.ownerQualifiedName() + "#" + symbolId.memberName();
        }
        if (symbolId.localId() != null) {
            return symbolId.ownerQualifiedName() + "#" + symbolId.localId();
        }
        return symbolId.ownerQualifiedName() == null ? symbolId.value() : symbolId.ownerQualifiedName();
    }
}

