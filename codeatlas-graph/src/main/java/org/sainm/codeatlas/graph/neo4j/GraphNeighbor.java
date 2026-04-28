package org.sainm.codeatlas.graph.neo4j;

import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.SourceType;

public record GraphNeighbor(
    String symbolId,
    String relationType,
    Confidence confidence,
    SourceType sourceType,
    String evidenceKey
) {
    public GraphNeighbor {
        if (symbolId == null || symbolId.isBlank()) {
            throw new IllegalArgumentException("symbolId is required");
        }
        relationType = relationType == null || relationType.isBlank() ? "UNKNOWN" : relationType.trim();
        confidence = confidence == null ? Confidence.UNKNOWN : confidence;
        sourceType = sourceType == null ? SourceType.STATIC_RULE : sourceType;
        evidenceKey = evidenceKey == null ? "" : evidenceKey.trim();
    }
}
