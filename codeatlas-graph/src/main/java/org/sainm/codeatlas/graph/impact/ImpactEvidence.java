package org.sainm.codeatlas.graph.impact;

import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.SourceType;
import java.util.Objects;

public record ImpactEvidence(
    String filePath,
    int lineNumber,
    String evidenceType,
    String snippet,
    SourceType sourceType,
    Confidence confidence
) {
    public ImpactEvidence {
        filePath = require(filePath, "filePath");
        if (lineNumber < 0) {
            throw new IllegalArgumentException("lineNumber must not be negative");
        }
        evidenceType = require(evidenceType, "evidenceType");
        snippet = snippet == null ? "" : snippet.trim();
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(confidence, "confidence");
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
