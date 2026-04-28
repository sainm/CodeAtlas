package org.sainm.codeatlas.graph.impact;

import java.util.List;

public record ImpactExplanation(
    String summary,
    List<String> evidencePath
) {
    public ImpactExplanation {
        summary = summary == null ? "" : summary.trim();
        evidencePath = List.copyOf(evidencePath);
    }
}
