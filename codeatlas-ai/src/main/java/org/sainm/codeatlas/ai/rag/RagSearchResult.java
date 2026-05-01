package org.sainm.codeatlas.ai.rag;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.sainm.codeatlas.graph.model.SymbolId;

public record RagSearchResult(
    SymbolId symbolId,
    String displayName,
    String summary,
    double score,
    Set<RagSearchMatchKind> matchKinds,
    List<String> evidenceKeys
) {
    public RagSearchResult {
        if (symbolId == null) {
            throw new IllegalArgumentException("symbolId is required");
        }
        displayName = displayName == null || displayName.isBlank() ? displayName(symbolId) : displayName.trim();
        summary = summary == null ? "" : summary.trim();
        score = Math.max(0.0d, Math.min(1.0d, score));
        matchKinds = matchKinds == null ? Set.of() : Set.copyOf(new TreeSet<>(matchKinds));
        evidenceKeys = evidenceKeys == null ? List.of() : List.copyOf(evidenceKeys);
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
