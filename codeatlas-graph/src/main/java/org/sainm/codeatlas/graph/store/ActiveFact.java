package org.sainm.codeatlas.graph.store;

import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.EvidenceKey;
import org.sainm.codeatlas.graph.model.FactKey;
import org.sainm.codeatlas.graph.model.SourceType;
import java.util.List;
import java.util.Set;

public record ActiveFact(
    FactKey factKey,
    List<EvidenceKey> evidenceKeys,
    Set<SourceType> sourceTypes,
    Confidence confidence
) {
    public ActiveFact {
        evidenceKeys = List.copyOf(evidenceKeys);
        sourceTypes = Set.copyOf(sourceTypes);
    }
}

