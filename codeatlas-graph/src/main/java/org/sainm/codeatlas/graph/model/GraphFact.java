package org.sainm.codeatlas.graph.model;

import java.util.Objects;

public record GraphFact(
    FactKey factKey,
    EvidenceKey evidenceKey,
    String projectId,
    String snapshotId,
    String analysisRunId,
    String scopeKey,
    Confidence confidence,
    SourceType sourceType,
    boolean active,
    boolean tombstone
) {
    public GraphFact {
        Objects.requireNonNull(factKey, "factKey");
        Objects.requireNonNull(evidenceKey, "evidenceKey");
        projectId = require(projectId, "projectId");
        snapshotId = require(snapshotId, "snapshotId");
        analysisRunId = require(analysisRunId, "analysisRunId");
        scopeKey = require(scopeKey, "scopeKey");
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(sourceType, "sourceType");
        if (tombstone && active) {
            throw new IllegalArgumentException("tombstone facts cannot be active");
        }
        if (sourceType == SourceType.AI_ASSISTED && confidence == Confidence.CERTAIN) {
            throw new IllegalArgumentException("AI assisted facts cannot be marked CERTAIN");
        }
    }

    public static GraphFact active(
        FactKey factKey,
        EvidenceKey evidenceKey,
        String projectId,
        String snapshotId,
        String analysisRunId,
        String scopeKey,
        Confidence confidence,
        SourceType sourceType
    ) {
        return new GraphFact(
            factKey,
            evidenceKey,
            projectId,
            snapshotId,
            analysisRunId,
            scopeKey,
            confidence,
            sourceType,
            true,
            false
        );
    }

    public GraphFact tombstone(String newSnapshotId, String newAnalysisRunId) {
        return new GraphFact(
            factKey,
            evidenceKey,
            projectId,
            newSnapshotId,
            newAnalysisRunId,
            scopeKey,
            confidence,
            sourceType,
            false,
            true
        );
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
