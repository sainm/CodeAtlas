package org.sainm.codeatlas.graph.impact;

import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import java.util.Objects;

public record ImpactPathStep(
    SymbolId symbolId,
    RelationType incomingRelation,
    SourceType sourceType,
    Confidence confidence
) {
    public ImpactPathStep {
        Objects.requireNonNull(symbolId, "symbolId");
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(confidence, "confidence");
    }
}
