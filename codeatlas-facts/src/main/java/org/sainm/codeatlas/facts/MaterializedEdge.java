package org.sainm.codeatlas.facts;

public record MaterializedEdge(
        String factKey,
        String sourceIdentityId,
        String targetIdentityId,
        RelationType relationType,
        RelationFamily relationFamily,
        String snapshotId,
        String evidenceKey,
        Confidence confidence,
        int priority,
        SourceType sourceType) {
    public MaterializedEdge {
        requireNonBlank(factKey, "factKey");
        requireNonBlank(sourceIdentityId, "sourceIdentityId");
        requireNonBlank(targetIdentityId, "targetIdentityId");
        if (relationType == null) {
            throw new IllegalArgumentException("relationType is required");
        }
        if (relationFamily == null) {
            throw new IllegalArgumentException("relationFamily is required");
        }
        if (relationFamily != relationType.family()) {
            throw new IllegalArgumentException("relationFamily must match relationType");
        }
        requireNonBlank(snapshotId, "snapshotId");
        requireNonBlank(evidenceKey, "evidenceKey");
        if (confidence == null) {
            throw new IllegalArgumentException("confidence is required");
        }
        if (priority < 0) {
            throw new IllegalArgumentException("priority must be non-negative");
        }
        if (sourceType == null) {
            throw new IllegalArgumentException("sourceType is required");
        }
    }

    public static MaterializedEdge from(FactRecord fact) {
        if (fact == null) {
            throw new IllegalArgumentException("fact is required");
        }
        if (!fact.active() || fact.tombstone()) {
            throw new IllegalArgumentException("materialized edge requires an active non-tombstone fact");
        }
        return new MaterializedEdge(
                fact.factKey(),
                fact.sourceIdentityId(),
                fact.targetIdentityId(),
                fact.relationType(),
                fact.relationFamily(),
                fact.snapshotId(),
                fact.evidenceKey(),
                fact.confidence(),
                fact.priority(),
                fact.sourceType());
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
