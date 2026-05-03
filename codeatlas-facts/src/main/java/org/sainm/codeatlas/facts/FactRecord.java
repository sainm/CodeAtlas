package org.sainm.codeatlas.facts;

import java.util.List;
import java.util.Objects;

import org.sainm.codeatlas.symbols.IdentityType;
import org.sainm.codeatlas.symbols.SymbolId;
import org.sainm.codeatlas.symbols.SymbolIdParser;

public final class FactRecord {
    private final String factKey;
    private final String sourceIdentityId;
    private final String targetIdentityId;
    private final RelationType relationType;
    private final String qualifier;
    private final String projectId;
    private final String snapshotId;
    private final String analysisRunId;
    private final String scopeRunId;
    private final String analyzerId;
    private final String scopeKey;
    private final RelationFamily relationFamily;
    private final int schemaVersion;
    private final boolean active;
    private final String validFromSnapshot;
    private final String validToSnapshot;
    private final boolean tombstone;
    private final String evidenceKey;
    private final Confidence confidence;
    private final int priority;
    private final SourceType sourceType;
    private final IdentityType identityType;

    public FactRecord(
            String factKey,
            String sourceIdentityId,
            String targetIdentityId,
            RelationType relationType,
            String qualifier,
            String projectId,
            String snapshotId,
            String analysisRunId,
            String scopeRunId,
            String analyzerId,
            String scopeKey,
            RelationFamily relationFamily,
            int schemaVersion,
            boolean active,
            String validFromSnapshot,
            String validToSnapshot,
            boolean tombstone,
            String evidenceKey,
            Confidence confidence,
            int priority,
            SourceType sourceType) {
        this(List.of(),
                factKey,
                sourceIdentityId,
                targetIdentityId,
                relationType,
                qualifier,
                projectId,
                snapshotId,
                analysisRunId,
                scopeRunId,
                analyzerId,
                scopeKey,
                relationFamily,
                schemaVersion,
                active,
                validFromSnapshot,
                validToSnapshot,
                tombstone,
                evidenceKey,
                confidence,
                priority,
                sourceType);
    }

    public FactRecord(
            List<String> identitySourceRoots,
            String factKey,
            String sourceIdentityId,
            String targetIdentityId,
            RelationType relationType,
            String qualifier,
            String projectId,
            String snapshotId,
            String analysisRunId,
            String scopeRunId,
            String analyzerId,
            String scopeKey,
            RelationFamily relationFamily,
            int schemaVersion,
            boolean active,
            String validFromSnapshot,
            String validToSnapshot,
            boolean tombstone,
            String evidenceKey,
            Confidence confidence,
            int priority,
            SourceType sourceType) {
        requireNonBlank(factKey, "factKey");
        List<String> scopedSourceRoots = identitySourceRoots == null ? List.of() : List.copyOf(identitySourceRoots);
        SymbolId sourceIdentity = requireIdentity(sourceIdentityId, "sourceIdentityId", scopedSourceRoots);
        SymbolId targetIdentity = requireIdentity(targetIdentityId, "targetIdentityId", scopedSourceRoots);
        if (relationType == null) {
            throw new IllegalArgumentException("relationType is required");
        }
        requireRegisteredRelation(relationType);
        String normalizedQualifier = qualifier == null ? "" : qualifier;
        requireNonBlank(projectId, "projectId");
        requireProjectIdentity(sourceIdentity, projectId, "sourceIdentityId");
        requireProjectIdentity(targetIdentity, projectId, "targetIdentityId");
        requireNonBlank(snapshotId, "snapshotId");
        requireNonBlank(analysisRunId, "analysisRunId");
        requireNonBlank(scopeRunId, "scopeRunId");
        requireNonBlank(analyzerId, "analyzerId");
        requireNonBlank(scopeKey, "scopeKey");
        if (relationFamily == null) {
            throw new IllegalArgumentException("relationFamily is required");
        }
        if (relationFamily != relationType.family()) {
            throw new IllegalArgumentException("relationFamily must match relationType");
        }
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        requireNonBlank(validFromSnapshot, "validFromSnapshot");
        String normalizedValidToSnapshot = validToSnapshot == null ? "" : validToSnapshot;
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
        requireAiCandidateBoundary(relationFamily, confidence, sourceType);
        String expectedFactKey = FactKey.of(
                sourceIdentityId,
                targetIdentityId,
                relationType.name(),
                normalizedQualifier).value();
        if (!factKey.equals(expectedFactKey)) {
            throw new IllegalArgumentException("factKey must match fact identity fields");
        }

        this.factKey = factKey;
        this.sourceIdentityId = sourceIdentityId;
        this.targetIdentityId = targetIdentityId;
        this.relationType = relationType;
        this.qualifier = normalizedQualifier;
        this.projectId = projectId;
        this.snapshotId = snapshotId;
        this.analysisRunId = analysisRunId;
        this.scopeRunId = scopeRunId;
        this.analyzerId = analyzerId;
        this.scopeKey = scopeKey;
        this.relationFamily = relationFamily;
        this.schemaVersion = schemaVersion;
        this.active = active;
        this.validFromSnapshot = validFromSnapshot;
        this.validToSnapshot = normalizedValidToSnapshot;
        this.tombstone = tombstone;
        this.evidenceKey = evidenceKey;
        this.confidence = confidence;
        this.priority = priority;
        this.sourceType = sourceType;
        this.identityType = targetIdentity.identityType();
    }

    public static FactRecord create(
            String sourceIdentityId,
            String targetIdentityId,
            String relationName,
            String qualifier,
            String projectId,
            String snapshotId,
            String analysisRunId,
            String scopeRunId,
            String analyzerId,
            String scopeKey,
            String evidenceKey,
            Confidence confidence,
            int priority,
            SourceType sourceType) {
        return create(
                List.of(),
                sourceIdentityId,
                targetIdentityId,
                relationName,
                qualifier,
                projectId,
                snapshotId,
                analysisRunId,
                scopeRunId,
                analyzerId,
                scopeKey,
                evidenceKey,
                confidence,
                priority,
                sourceType);
    }

    public static FactRecord create(
            List<String> identitySourceRoots,
            String sourceIdentityId,
            String targetIdentityId,
            String relationName,
            String qualifier,
            String projectId,
            String snapshotId,
            String analysisRunId,
            String scopeRunId,
            String analyzerId,
            String scopeKey,
            String evidenceKey,
            Confidence confidence,
            int priority,
            SourceType sourceType) {
        RelationType relationType = RelationKindRegistry.defaults().require(relationName);
        return new FactRecord(
                identitySourceRoots,
                FactKey.of(sourceIdentityId, targetIdentityId, relationName, qualifier).value(),
                sourceIdentityId,
                targetIdentityId,
                relationType,
                qualifier,
                projectId,
                snapshotId,
                analysisRunId,
                scopeRunId,
                analyzerId,
                scopeKey,
                relationType.family(),
                1,
                true,
                snapshotId,
                "",
                false,
                evidenceKey,
                confidence,
                priority,
                sourceType);
    }

    public String factKey() {
        return factKey;
    }

    public String sourceIdentityId() {
        return sourceIdentityId;
    }

    public String targetIdentityId() {
        return targetIdentityId;
    }

    public RelationType relationType() {
        return relationType;
    }

    public String qualifier() {
        return qualifier;
    }

    public String projectId() {
        return projectId;
    }

    public String snapshotId() {
        return snapshotId;
    }

    public String analysisRunId() {
        return analysisRunId;
    }

    public String scopeRunId() {
        return scopeRunId;
    }

    public String analyzerId() {
        return analyzerId;
    }

    public String scopeKey() {
        return scopeKey;
    }

    public RelationFamily relationFamily() {
        return relationFamily;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public boolean active() {
        return active;
    }

    public String validFromSnapshot() {
        return validFromSnapshot;
    }

    public String validToSnapshot() {
        return validToSnapshot;
    }

    public boolean tombstone() {
        return tombstone;
    }

    public String evidenceKey() {
        return evidenceKey;
    }

    public Confidence confidence() {
        return confidence;
    }

    public int priority() {
        return priority;
    }

    public SourceType sourceType() {
        return sourceType;
    }

    public IdentityType identityType() {
        return identityType;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof FactRecord other)) {
            return false;
        }
        return schemaVersion == other.schemaVersion
                && active == other.active
                && tombstone == other.tombstone
                && priority == other.priority
                && factKey.equals(other.factKey)
                && sourceIdentityId.equals(other.sourceIdentityId)
                && targetIdentityId.equals(other.targetIdentityId)
                && relationType.equals(other.relationType)
                && qualifier.equals(other.qualifier)
                && projectId.equals(other.projectId)
                && snapshotId.equals(other.snapshotId)
                && analysisRunId.equals(other.analysisRunId)
                && scopeRunId.equals(other.scopeRunId)
                && analyzerId.equals(other.analyzerId)
                && scopeKey.equals(other.scopeKey)
                && relationFamily == other.relationFamily
                && validFromSnapshot.equals(other.validFromSnapshot)
                && validToSnapshot.equals(other.validToSnapshot)
                && evidenceKey.equals(other.evidenceKey)
                && confidence == other.confidence
                && sourceType == other.sourceType
                && identityType == other.identityType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                factKey,
                sourceIdentityId,
                targetIdentityId,
                relationType,
                qualifier,
                projectId,
                snapshotId,
                analysisRunId,
                scopeRunId,
                analyzerId,
                scopeKey,
                relationFamily,
                schemaVersion,
                active,
                validFromSnapshot,
                validToSnapshot,
                tombstone,
                evidenceKey,
                confidence,
                priority,
                sourceType,
                identityType);
    }

    @Override
    public String toString() {
        return "FactRecord["
                + "factKey=" + factKey
                + ", sourceIdentityId=" + sourceIdentityId
                + ", targetIdentityId=" + targetIdentityId
                + ", relationType=" + relationType
                + ", qualifier=" + qualifier
                + ", projectId=" + projectId
                + ", snapshotId=" + snapshotId
                + ", analysisRunId=" + analysisRunId
                + ", scopeRunId=" + scopeRunId
                + ", analyzerId=" + analyzerId
                + ", scopeKey=" + scopeKey
                + ", relationFamily=" + relationFamily
                + ", schemaVersion=" + schemaVersion
                + ", active=" + active
                + ", validFromSnapshot=" + validFromSnapshot
                + ", validToSnapshot=" + validToSnapshot
                + ", tombstone=" + tombstone
                + ", evidenceKey=" + evidenceKey
                + ", confidence=" + confidence
                + ", priority=" + priority
                + ", sourceType=" + sourceType
                + ", identityType=" + identityType
                + ']';
    }

    private static SymbolId requireIdentity(String value, String name, List<String> identitySourceRoots) {
        requireNonBlank(value, name);
        if (identitySourceRoots.isEmpty()) {
            return SymbolIdParser.parse(value);
        }
        return SymbolIdParser.withSourceRoots(identitySourceRoots).parseId(value);
    }

    private static void requireProjectIdentity(SymbolId identity, String projectId, String name) {
        if (!identity.projectKey().equals(projectId)) {
            throw new IllegalArgumentException(name + " projectKey must match projectId");
        }
    }

    private static void requireRegisteredRelation(RelationType relationType) {
        RelationType registered = RelationKindRegistry.defaults().require(relationType.name());
        if (!registered.equals(relationType)) {
            throw new IllegalArgumentException("relationType must match the registered relation contract");
        }
    }

    private static void requireAiCandidateBoundary(
            RelationFamily relationFamily,
            Confidence confidence,
            SourceType sourceType) {
        if (sourceType == SourceType.AI_ASSISTED && relationFamily != RelationFamily.AI_ASSISTED_CANDIDATE) {
            throw new IllegalArgumentException("AI assisted facts must stay in the AI assisted candidate relation family");
        }
        if (relationFamily == RelationFamily.AI_ASSISTED_CANDIDATE && sourceType != SourceType.AI_ASSISTED) {
            throw new IllegalArgumentException("AI assisted candidate facts must come from AI assisted source type");
        }
        if (sourceType == SourceType.AI_ASSISTED && confidence == Confidence.CERTAIN) {
            throw new IllegalArgumentException("AI assisted facts cannot be CERTAIN");
        }
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
