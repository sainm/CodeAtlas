package org.sainm.codeatlas.ai.rag;

import org.sainm.codeatlas.ai.summary.ArtifactSummary;

public record SemanticSearchResult(
    ArtifactSummary summary,
    double score
) {
    public SemanticSearchResult {
        if (summary == null) {
            throw new IllegalArgumentException("summary is required");
        }
        score = Math.max(0.0d, Math.min(1.0d, score));
    }
}
