package org.sainm.codeatlas.facts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryFactStoreTest {
    private InMemoryFactStore store;

    @BeforeEach
    void setUp() {
        store = InMemoryFactStore.defaults();
    }

    @Test
    void returnsActiveFactsFilteredByProjectAndSnapshot() {
        FactRecord active = fact("shop", "snap-1", "CALLS", "ev-1");
        FactRecord tombstoned = tombstone(fact("shop", "snap-1", "CALLS", "ev-2"));
        FactRecord otherProject = fact("other", "snap-1", "CALLS", "ev-3");
        FactRecord otherSnapshot = fact("shop", "snap-2", "CALLS", "ev-4");

        store.insertAll(List.of(active, tombstoned, otherProject, otherSnapshot));

        List<FactRecord> result = store.activeFacts("shop", "snap-1");
        assertEquals(1, result.size());
        assertEquals(active, result.getFirst());
    }

    @Test
    void queriesFactsByRelationType() {
        FactRecord calls = fact("shop", "snap-1", "CALLS", "ev-1");
        FactRecord reads = fact("shop", "snap-1", "READS_TABLE", "ev-2");

        store.insertAll(List.of(calls, reads));

        assertEquals(1, store.factsByRelation("shop", "snap-1", "CALLS").size());
        assertEquals(1, store.factsByRelation("shop", "snap-1", "READS_TABLE").size());
        assertEquals(0, store.factsByRelation("shop", "snap-1", "WRITES_TABLE").size());
    }

    @Test
    void queriesFactsBySourceAndTarget() {
        FactRecord fact = fact("shop", "snap-1", "CALLS", "ev-1");
        store.insert(fact);

        assertEquals(1, store.factsBySource("shop", "snap-1", "method://shop/_root/src/main/java/A#m()V").size());
        assertEquals(1, store.factsByTarget("shop", "snap-1", "method://shop/_root/src/main/java/B#n()V").size());
        assertEquals(0, store.factsBySource("shop", "snap-1", "nonexistent").size());
    }

    @Test
    void tombstonesExpiredFacts() {
        FactRecord active1 = fact("shop", "snap-1", "CALLS", "ev-1");
        FactRecord active2 = fact("shop", "snap-1", "CALLS", "ev-2");
        FactRecord tombstoned = tombstone(fact("shop", "snap-1", "CALLS", "ev-3"));

        store.insertAll(List.of(active1, active2, tombstoned));

        int removed = store.tombstoneExpired("shop", "snap-1");
        assertEquals(1, removed);
        assertEquals(2, store.activeFacts("shop", "snap-1").size());
    }

    @Test
    void listsActiveSnapshots() {
        store.insertAll(List.of(
                fact("shop", "snap-1", "CALLS", "ev-1"),
                fact("shop", "snap-2", "CALLS", "ev-2"),
                fact("other", "snap-3", "CALLS", "ev-3")));

        assertEquals(2, store.activeSnapshots("shop").size());
        assertTrue(store.activeSnapshots("shop").contains("snap-1"));
        assertTrue(store.activeSnapshots("shop").contains("snap-2"));
        assertEquals(1, store.activeSnapshots("other").size());
    }

    @Test
    void upsertsReplacingExistingFactsWithSameKey() {
        String qualifier = "direct";
        FactRecord original = FactRecord.create(
                List.of("src/main/java"),
                "method://shop/_root/src/main/java/A#m()V",
                "method://shop/_root/src/main/java/B#n()V",
                "CALLS",
                qualifier,
                "shop",
                "snap-1",
                "analysis-1",
                "scope-1",
                "spoon",
                "src/main/java",
                "ev-1",
                Confidence.CERTAIN,
                100,
                SourceType.SPOON);
        store.insert(original);

        FactRecord updated = FactRecord.create(
                List.of("src/main/java"),
                original.sourceIdentityId(),
                original.targetIdentityId(),
                "CALLS",
                qualifier,
                original.projectId(),
                original.snapshotId(),
                original.analysisRunId(),
                original.scopeRunId(),
                original.analyzerId(),
                original.scopeKey(),
                "ev-updated",
                Confidence.LIKELY,
                50,
                SourceType.SPOON);

        store.upsertAll(List.of(updated));

        List<FactRecord> result = store.activeFacts("shop", "snap-1");
        assertEquals(1, result.size());
        assertEquals("ev-updated", result.getFirst().evidenceKey());
        assertEquals(Confidence.LIKELY, result.getFirst().confidence());
    }

    @Test
    void generatesReport() {
        store.insertAll(List.of(
                fact("shop", "snap-1", "CALLS", "ev-1"),
                fact("shop", "snap-1", "READS_TABLE", "ev-2")));

        CurrentFactReport report = store.report("shop", "snap-1");
        assertEquals("shop", report.projectId());
        assertEquals(2, report.facts().size());
        assertEquals(2, report.edges().size());
    }

    private static FactRecord fact(String projectId, String snapshotId, String relation, String evidenceKey) {
        return FactRecord.create(
                List.of("src/main/java"),
                "method://" + projectId + "/_root/src/main/java/A#m()V",
                "method://" + projectId + "/_root/src/main/java/B#n()V",
                relation,
                "direct",
                projectId,
                snapshotId,
                "analysis-1",
                "scope-1",
                "spoon",
                "src/main/java",
                evidenceKey,
                Confidence.CERTAIN,
                100,
                SourceType.SPOON);
    }

    private static FactRecord tombstone(FactRecord original) {
        return new FactRecord(
                List.of(),
                original.factKey(),
                original.sourceIdentityId(),
                original.targetIdentityId(),
                original.relationType(),
                original.qualifier(),
                original.projectId(),
                original.snapshotId(),
                original.analysisRunId(),
                original.scopeRunId(),
                original.analyzerId(),
                original.scopeKey(),
                original.relationFamily(),
                original.schemaVersion(),
                original.active(),
                original.validFromSnapshot(),
                original.validToSnapshot(),
                true,
                original.evidenceKey(),
                original.confidence(),
                original.priority(),
                original.sourceType());
    }
}
