package org.sainm.codeatlas.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sainm.codeatlas.facts.Confidence;
import org.sainm.codeatlas.facts.CurrentFactReport;
import org.sainm.codeatlas.facts.FactRecord;
import org.sainm.codeatlas.facts.SourceType;

class ImpactAnalysisRegressionTest {
    @Test
    void deletedRelationshipsDoNotAppearInCurrentReports() {
        FactRecord active = call(method("com.acme.A", "a"), method("com.acme.B", "b"));
        FactRecord deleted = tombstone(call(method("com.acme.C", "c"), method("com.acme.D", "d")));

        CurrentFactReport report = CurrentFactReport.from("shop", List.of(active, deleted));

        assertEquals(List.of(active), report.facts());
        assertTrue(report.edges().stream().noneMatch(edge -> edge.factKey().equals(deleted.factKey())));
    }

    @Test
    void changedSnapshotInvalidationDoesNotClearOtherSnapshotCaches() {
        String a = method("com.acme.A", "a");
        String b = method("com.acme.B", "b");
        CurrentFactReport report = CurrentFactReport.from("shop", List.of(call(a, b)));
        InMemoryAdjacencyCacheStore store = new InMemoryAdjacencyCacheStore();
        store.rebuildPrimitiveCallCache(report, "snapshot-1");
        store.rebuildPrimitiveCallCache(report, "snapshot-2");

        store.invalidateProjectSnapshot("shop", "snapshot-1");

        assertTrue(store.primitiveCallCache("shop", "snapshot-1").isEmpty());
        assertTrue(store.primitiveCallCache("shop", "snapshot-2").isPresent());
    }

    @Test
    void reportsExposeAiDisabledAndTruncationState() {
        FastImpactReport report = new FastImpactReport(
                "shop",
                "snapshot-1",
                List.of(method("com.acme.A", "a")),
                List.of(),
                List.of(),
                true);

        assertFalse(report.aiEnabled());
        assertTrue(report.truncated());
        assertTrue(report.toJson().contains("\"aiEnabled\":false"));
        assertTrue(MarkdownImpactReportExporter.defaults().export(report).contains("AI Enabled: `false`"));
    }

    @Test
    void traversalReportsTruncationWhenPathLimitIsReached() {
        String changed = method("com.acme.Target", "run");
        CurrentFactReport report = CurrentFactReport.from("shop", List.of(
                call(method("com.acme.A", "a"), changed),
                call(method("com.acme.B", "b"), changed)));

        CallerTraversalResult result = CallerTraversalEngine.defaults().findCallers(report, changed, 2, 1);

        assertEquals(1, result.callerPaths().size());
        assertTrue(result.truncated());
    }

    private static FactRecord tombstone(FactRecord fact) {
        return new FactRecord(
                List.of("src/main/java"),
                fact.factKey(),
                fact.sourceIdentityId(),
                fact.targetIdentityId(),
                fact.relationType(),
                fact.qualifier(),
                fact.projectId(),
                fact.snapshotId(),
                fact.analysisRunId(),
                fact.scopeRunId(),
                fact.analyzerId(),
                fact.scopeKey(),
                fact.relationFamily(),
                fact.schemaVersion(),
                fact.active(),
                fact.validFromSnapshot(),
                fact.validToSnapshot(),
                true,
                fact.evidenceKey(),
                fact.confidence(),
                fact.priority(),
                fact.sourceType());
    }

    private static FactRecord call(String source, String target) {
        return FactRecord.create(
                List.of("src/main/java"),
                source,
                target,
                "CALLS",
                "direct",
                "shop",
                "snapshot-1",
                "analysis-1",
                "scope-1",
                "spoon",
                "src/main/java",
                "evidence-1",
                Confidence.CERTAIN,
                100,
                SourceType.SPOON);
    }

    private static String method(String owner, String method) {
        return "method://shop/_root/src/main/java/" + owner + "#" + method + "()V";
    }
}
