package org.sainm.codeatlas.ai.agent;

public record AgentEvidenceRef(
    String sourceType,
    String confidence,
    String evidenceKey,
    String symbolId,
    String relationType
) {
    public AgentEvidenceRef {
        sourceType = require(sourceType, "sourceType");
        confidence = require(confidence, "confidence");
        evidenceKey = trim(evidenceKey);
        symbolId = trim(symbolId);
        relationType = trim(relationType);
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
