package org.sainm.codeatlas.graph;

import java.util.List;

import org.sainm.codeatlas.facts.Confidence;
import org.sainm.codeatlas.facts.SourceType;

public record ImpactPathDetail(
        ImpactPath path,
        String risk,
        Confidence confidence,
        SourceType sourceType,
        List<String> evidenceKeys) {
    public ImpactPathDetail {
        if (path == null) {
            throw new IllegalArgumentException("path is required");
        }
        risk = risk == null || risk.isBlank() ? "UNKNOWN" : risk;
        confidence = confidence == null ? Confidence.UNKNOWN : confidence;
        sourceType = sourceType == null ? SourceType.BOUNDARY_SYMBOL : sourceType;
        evidenceKeys = List.copyOf(evidenceKeys == null ? List.of() : evidenceKeys);
    }
}
