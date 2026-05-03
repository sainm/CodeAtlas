package org.sainm.codeatlas.facts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class FactRecordTest {
    @Test
    void buildsStableFactKeysFromSourceTargetRelationAndQualifier() {
        FactKey directCall = FactKey.of(
                "method://shop/_root/src/main/java/com.foo.A#a()V",
                "method://shop/_root/src/main/java/com.foo.B#b()V",
                "CALLS",
                "direct");
        FactKey sameDirectCall = FactKey.of(
                "method://shop/_root/src/main/java/com.foo.A#a()V",
                "method://shop/_root/src/main/java/com.foo.B#b()V",
                "CALLS",
                "direct");
        FactKey routedCall = FactKey.of(
                "method://shop/_root/src/main/java/com.foo.A#a()V",
                "method://shop/_root/src/main/java/com.foo.B#b()V",
                "CALLS",
                "routed");

        assertEquals(directCall, sameDirectCall);
        assertNotEquals(directCall, routedCall);
        assertTrue(directCall.value().startsWith("fact:"));
    }

    @Test
    void buildsEvidenceWithStableKeys() {
        Evidence evidence = Evidence.create(
                "spoon",
                "src/main/java/com/foo/A.java",
                "src/main/java/com/foo/A.java",
                "line:12-14",
                1,
                SourceType.SPOON);

        assertEquals(evidence.evidenceKey(), EvidenceKey.of(
                "spoon",
                "src/main/java/com/foo/A.java",
                "src/main/java/com/foo/A.java",
                "line:12-14",
                1).value());
        assertSame(SourceType.SPOON, evidence.sourceType());
    }

    @Test
    void createsFactRecordWithRequiredAuditFields() {
        Evidence evidence = Evidence.create(
                "spoon",
                "src/main/java/com/foo/A.java",
                "src/main/java/com/foo/A.java",
                "line:12-14",
                1,
                SourceType.SPOON);

        FactRecord fact = FactRecord.create(
                "method://shop/_root/src/main/java/com.foo.A#a()V",
                "method://shop/_root/src/main/java/com.foo.B#b()V",
                "CALLS",
                "direct",
                "shop",
                "snapshot-1",
                "analysis-1",
                "scope-1",
                "spoon",
                "src/main/java/com/foo/A.java",
                evidence.evidenceKey(),
                Confidence.CERTAIN,
                100,
                SourceType.SPOON);

        assertEquals("CALLS", fact.relationType().name());
        assertSame(RelationFamily.CALL, fact.relationFamily());
        assertEquals("snapshot-1", fact.validFromSnapshot());
        assertEquals("", fact.validToSnapshot());
        assertTrue(fact.active());
        assertEquals(FactKey.of(
                fact.sourceIdentityId(),
                fact.targetIdentityId(),
                fact.relationType().name(),
                fact.qualifier()).value(), fact.factKey());

        MaterializedEdge edge = MaterializedEdge.from(fact);

        assertEquals(fact.factKey(), edge.factKey());
        assertEquals(fact.sourceIdentityId(), edge.sourceIdentityId());
        assertEquals(fact.targetIdentityId(), edge.targetIdentityId());
        assertEquals(fact.relationType(), edge.relationType());
        assertSame(fact.confidence(), edge.confidence());
        assertEquals(fact.evidenceKey(), edge.evidenceKey());
    }

    @Test
    void validatesFactIdentitiesWithScopedSourceRoots() {
        Evidence evidence = Evidence.create(
                "spring",
                "generated/sources/annotations/java/main",
                "generated/sources/annotations/java/main/com/foo/GeneratedEndpoint.java",
                "request-mapping:/orders",
                1,
                SourceType.SPRING);

        FactRecord fact = FactRecord.create(
                List.of("generated/sources/annotations/java/main", "src/main/java"),
                "api-endpoint://shop/api/generated/sources/annotations/java/main/GET:/orders",
                "method://shop/api/src/main/java/com.foo.GeneratedEndpoint#orders()V",
                "ROUTES_TO",
                "spring-mapping",
                "shop",
                "snapshot-1",
                "analysis-1",
                "scope-1",
                "spring",
                "generated/sources/annotations/java/main",
                evidence.evidenceKey(),
                Confidence.CERTAIN,
                100,
                SourceType.SPRING);

        assertEquals("api-endpoint://shop/api/generated/sources/annotations/java/main/GET:/orders",
                fact.sourceIdentityId());
    }

    @Test
    void rejectsIdentitiesFromDifferentProject() {
        Evidence evidence = Evidence.create(
                "spoon",
                "src/main/java/com/foo/A.java",
                "src/main/java/com/foo/A.java",
                "line:12-14",
                1,
                SourceType.SPOON);

        assertThrows(IllegalArgumentException.class, () -> FactRecord.create(
                "method://billing/_root/src/main/java/com.foo.A#a()V",
                "method://shop/_root/src/main/java/com.foo.B#b()V",
                "CALLS",
                "direct",
                "shop",
                "snapshot-1",
                "analysis-1",
                "scope-1",
                "spoon",
                "src/main/java/com/foo/A.java",
                evidence.evidenceKey(),
                Confidence.CERTAIN,
                100,
                SourceType.SPOON));
        assertThrows(IllegalArgumentException.class, () -> FactRecord.create(
                "method://shop/_root/src/main/java/com.foo.A#a()V",
                "method://billing/_root/src/main/java/com.foo.B#b()V",
                "CALLS",
                "direct",
                "shop",
                "snapshot-1",
                "analysis-1",
                "scope-1",
                "spoon",
                "src/main/java/com/foo/A.java",
                evidence.evidenceKey(),
                Confidence.CERTAIN,
                100,
                SourceType.SPOON));
    }

    @Test
    void scopedSourceRootsValidateWithoutBecomingFactIdentity() {
        Evidence evidence = Evidence.create(
                "spring",
                "generated/sources/annotations/java/main",
                "generated/sources/annotations/java/main/com/foo/GeneratedEndpoint.java",
                "request-mapping:/orders",
                1,
                SourceType.SPRING);

        FactRecord first = FactRecord.create(
                List.of("generated/sources/annotations/java/main", "src/main/java"),
                "api-endpoint://shop/api/generated/sources/annotations/java/main/GET:/orders",
                "method://shop/api/src/main/java/com.foo.GeneratedEndpoint#orders()V",
                "ROUTES_TO",
                "spring-mapping",
                "shop",
                "snapshot-1",
                "analysis-1",
                "scope-1",
                "spring",
                "generated/sources/annotations/java/main",
                evidence.evidenceKey(),
                Confidence.CERTAIN,
                100,
                SourceType.SPRING);
        FactRecord sameFactWithDifferentValidationOrder = FactRecord.create(
                List.of("src/main/java", "generated/sources/annotations/java/main"),
                "api-endpoint://shop/api/generated/sources/annotations/java/main/GET:/orders",
                "method://shop/api/src/main/java/com.foo.GeneratedEndpoint#orders()V",
                "ROUTES_TO",
                "spring-mapping",
                "shop",
                "snapshot-1",
                "analysis-1",
                "scope-1",
                "spring",
                "generated/sources/annotations/java/main",
                evidence.evidenceKey(),
                Confidence.CERTAIN,
                100,
                SourceType.SPRING);

        assertEquals(first, sameFactWithDifferentValidationOrder);
        assertEquals(first.hashCode(), sameFactWithDifferentValidationOrder.hashCode());
    }

    @Test
    void reconstructsFactRecordsWithScopedSourceRoots() {
        Evidence evidence = Evidence.create(
                "spring",
                "generated/sources/annotations/java/main",
                "generated/sources/annotations/java/main/com/foo/GeneratedEndpoint.java",
                "request-mapping:/orders",
                1,
                SourceType.SPRING);
        FactRecord created = FactRecord.create(
                List.of("generated/sources/annotations/java/main", "src/main/java"),
                "api-endpoint://shop/api/generated/sources/annotations/java/main/GET:/orders",
                "method://shop/api/src/main/java/com.foo.GeneratedEndpoint#orders()V",
                "ROUTES_TO",
                "spring-mapping",
                "shop",
                "snapshot-1",
                "analysis-1",
                "scope-1",
                "spring",
                "generated/sources/annotations/java/main",
                evidence.evidenceKey(),
                Confidence.CERTAIN,
                100,
                SourceType.SPRING);

        FactRecord reconstructed = new FactRecord(
                List.of("generated/sources/annotations/java/main", "src/main/java"),
                created.factKey(),
                created.sourceIdentityId(),
                created.targetIdentityId(),
                created.relationType(),
                created.qualifier(),
                created.projectId(),
                created.snapshotId(),
                created.analysisRunId(),
                created.scopeRunId(),
                created.analyzerId(),
                created.scopeKey(),
                created.relationFamily(),
                created.schemaVersion(),
                created.active(),
                created.validFromSnapshot(),
                created.validToSnapshot(),
                created.tombstone(),
                created.evidenceKey(),
                created.confidence(),
                created.priority(),
                created.sourceType());

        assertEquals(created, reconstructed);
    }

    @Test
    void rejectsAiAssistedFactsOutsideAiCandidateBoundary() {
        Evidence evidence = Evidence.create(
                "ai",
                "src/main/java/com/foo/A.java",
                "src/main/java/com/foo/A.java",
                "candidate",
                1,
                SourceType.AI_ASSISTED);

        assertThrows(IllegalArgumentException.class, () -> FactRecord.create(
                "method://shop/_root/src/main/java/com.foo.A#a()V",
                "method://shop/_root/src/main/java/com.foo.B#b()V",
                "REFLECTS_TO",
                "ai-candidate",
                "shop",
                "snapshot-1",
                "analysis-1",
                "scope-1",
                "ai",
                "src/main/java/com/foo/A.java",
                evidence.evidenceKey(),
                Confidence.CERTAIN,
                100,
                SourceType.AI_ASSISTED));
    }

    @Test
    void keepsReflectionCandidatesSeparateFromAiCandidates() {
        Evidence reflectionEvidence = Evidence.create(
                "reflection",
                "src/main/java/com/foo/A.java",
                "src/main/java/com/foo/A.java",
                "Class.forName",
                1,
                SourceType.REFLECTION);
        Evidence aiEvidence = Evidence.create(
                "ai",
                "src/main/java/com/foo/A.java",
                "src/main/java/com/foo/A.java",
                "candidate",
                1,
                SourceType.AI_ASSISTED);

        FactRecord reflectionFact = FactRecord.create(
                "reflection-candidate://shop/_root/src/main/java/com.foo.A#a()V:reflect[com.foo.B]",
                "method://shop/_root/src/main/java/com.foo.B#b()V",
                "REFLECTS_TO",
                "reflection-candidate",
                "shop",
                "snapshot-1",
                "analysis-1",
                "scope-1",
                "reflection",
                "src/main/java/com/foo/A.java",
                reflectionEvidence.evidenceKey(),
                Confidence.POSSIBLE,
                50,
                SourceType.REFLECTION);
        FactRecord aiFact = FactRecord.create(
                "method://shop/_root/src/main/java/com.foo.A#a()V",
                "method://shop/_root/src/main/java/com.foo.B#b()V",
                "AI_SUGGESTS_RELATION",
                "ai-candidate",
                "shop",
                "snapshot-1",
                "analysis-1",
                "scope-1",
                "ai",
                "src/main/java/com/foo/A.java",
                aiEvidence.evidenceKey(),
                Confidence.POSSIBLE,
                10,
                SourceType.AI_ASSISTED);

        assertSame(RelationFamily.CANDIDATE, reflectionFact.relationFamily());
        assertSame(RelationFamily.AI_ASSISTED_CANDIDATE, aiFact.relationFamily());
        assertThrows(IllegalArgumentException.class, () -> FactRecord.create(
                "method://shop/_root/src/main/java/com.foo.A#a()V",
                "method://shop/_root/src/main/java/com.foo.B#b()V",
                "AI_SUGGESTS_RELATION",
                "static-candidate",
                "shop",
                "snapshot-1",
                "analysis-1",
                "scope-1",
                "reflection",
                "src/main/java/com/foo/A.java",
                reflectionEvidence.evidenceKey(),
                Confidence.POSSIBLE,
                10,
                SourceType.REFLECTION));
        assertThrows(IllegalArgumentException.class, () -> FactRecord.create(
                "method://shop/_root/src/main/java/com.foo.A#a()V",
                "method://shop/_root/src/main/java/com.foo.B#b()V",
                "AI_SUGGESTS_RELATION",
                "ai-candidate",
                "shop",
                "snapshot-1",
                "analysis-1",
                "scope-1",
                "ai",
                "src/main/java/com/foo/A.java",
                aiEvidence.evidenceKey(),
                Confidence.CERTAIN,
                10,
                SourceType.AI_ASSISTED));
    }

    @Test
    void rejectsInvalidFactContracts() {
        Evidence evidence = Evidence.create(
                "spoon",
                "src/main/java/com/foo/A.java",
                "src/main/java/com/foo/A.java",
                "line:12-14",
                1,
                SourceType.SPOON);

        assertThrows(IllegalArgumentException.class, () -> new Evidence(
                "evidence:wrong",
                "spoon",
                "src/main/java/com/foo/A.java",
                "src/main/java/com/foo/A.java",
                "line:12-14",
                1,
                SourceType.SPOON));
        assertThrows(IllegalArgumentException.class, () -> new FactRecord(
                "fact:wrong",
                "method://shop/_root/src/main/java/com.foo.A#a()V",
                "method://shop/_root/src/main/java/com.foo.B#b()V",
                RelationKindRegistry.defaults().require("CALLS"),
                "direct",
                "shop",
                "snapshot-1",
                "analysis-1",
                "scope-1",
                "spoon",
                "src/main/java/com/foo/A.java",
                RelationFamily.CALL,
                1,
                true,
                "snapshot-1",
                "",
                false,
                evidence.evidenceKey(),
                Confidence.CERTAIN,
                100,
                SourceType.SPOON));
        assertThrows(IllegalArgumentException.class, () -> FactRecord.create(
                "method://shop/_root/src/main/java/com.foo.A#a()V",
                "method://shop/_root/src/main/java/com.foo.B#b()V",
                "CALLS",
                "direct",
                "shop",
                "snapshot-1",
                "analysis-1",
                "scope-1",
                "ai",
                "src/main/java/com/foo/A.java",
                evidence.evidenceKey(),
                Confidence.CERTAIN,
                100,
                SourceType.AI_ASSISTED));
        RelationType unregisteredRelation = new RelationType("CALLS_MAYBE", RelationFamily.CALL, true);
        assertThrows(IllegalArgumentException.class, () -> new FactRecord(
                FactKey.of(
                        "method://shop/_root/src/main/java/com.foo.A#a()V",
                        "method://shop/_root/src/main/java/com.foo.B#b()V",
                        unregisteredRelation.name(),
                        "direct").value(),
                "method://shop/_root/src/main/java/com.foo.A#a()V",
                "method://shop/_root/src/main/java/com.foo.B#b()V",
                unregisteredRelation,
                "direct",
                "shop",
                "snapshot-1",
                "analysis-1",
                "scope-1",
                "spoon",
                "src/main/java/com/foo/A.java",
                RelationFamily.CALL,
                1,
                true,
                "snapshot-1",
                "",
                false,
                evidence.evidenceKey(),
                Confidence.CERTAIN,
                100,
                SourceType.SPOON));
        assertThrows(IllegalArgumentException.class, () -> FactRecord.create(
                "not-a-symbol",
                "method://shop/_root/src/main/java/com.foo.B#b()V",
                "CALLS",
                "direct",
                "shop",
                "snapshot-1",
                "analysis-1",
                "scope-1",
                "spoon",
                "src/main/java/com/foo/A.java",
                "evidence:1",
                Confidence.CERTAIN,
                100,
                SourceType.SPOON));
        assertThrows(IllegalArgumentException.class, () -> FactRecord.create(
                "method://shop/_root/src/main/java/com.foo.A#a()V",
                "method://shop/_root/src/main/java/com.foo.B#b()V",
                "CALLS_MAYBE",
                "direct",
                "shop",
                "snapshot-1",
                "analysis-1",
                "scope-1",
                "spoon",
                "src/main/java/com/foo/A.java",
                "evidence:1",
                Confidence.CERTAIN,
                100,
                SourceType.SPOON));
    }
}
