package org.sainm.codeatlas.graph.flow;

import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.EvidenceKey;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import java.util.List;
import java.util.Objects;

public record GraphFlowStep(
    SymbolId symbolId,
    RelationType incomingRelation,
    String qualifier,
    SourceType sourceType,
    Confidence confidence,
    List<EvidenceKey> evidenceKeys
) {
    public GraphFlowStep {
        Objects.requireNonNull(symbolId, "symbolId");
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(confidence, "confidence");
        qualifier = qualifier == null ? "" : qualifier.trim();
        evidenceKeys = List.copyOf(evidenceKeys);
    }
}
