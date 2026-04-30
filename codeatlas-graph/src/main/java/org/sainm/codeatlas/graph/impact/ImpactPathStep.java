package org.sainm.codeatlas.graph.impact;

import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.EvidenceKey;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import java.util.List;
import java.util.Objects;

public record ImpactPathStep(
    SymbolId symbolId,
    RelationType incomingRelation,
    SourceType sourceType,
    Confidence confidence,
    List<EvidenceKey> evidenceKeys
) {
    public ImpactPathStep(SymbolId symbolId, RelationType incomingRelation, SourceType sourceType, Confidence confidence) {
        this(symbolId, incomingRelation, sourceType, confidence, List.of());
    }

    public ImpactPathStep {
        Objects.requireNonNull(symbolId, "symbolId");
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(confidence, "confidence");
        evidenceKeys = List.copyOf(evidenceKeys);
    }
}
