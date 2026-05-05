package org.sainm.codeatlas.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sainm.codeatlas.facts.Confidence;
import org.sainm.codeatlas.facts.FactRecord;
import org.sainm.codeatlas.facts.FactStore;
import org.sainm.codeatlas.facts.InMemoryFactStore;
import org.sainm.codeatlas.facts.SourceType;

/**
 * Labeled evaluation data for false-positive / false-negative measurement.
 *
 * <p>Creates a known graph of Controller → Service → DAO → SQL and verifies
 * that impact analysis correctly identifies the expected paths while not
 * reporting known false-positive candidates.
 */
class EvaluationLabeledDataTest {

    private static final String PROJ = "eval-proj";
    private static final String SNAP = "snap-1";

    private static String m(String name) {
        return "method://" + PROJ + "/_root/src/main/java/com/acme/" + name + "#run()V";
    }

    private static FactRecord call(String src, String tgt) {
        return FactRecord.create(
                List.of("src/main/java"),
                src, tgt, "CALLS", "direct",
                PROJ, SNAP, "run-1", "scope-1", "spoon", "src/main/java",
                "ev-" + src.hashCode() + "-" + tgt.hashCode(),
                Confidence.CERTAIN, 100, SourceType.SPOON);
    }

    @Test
    void evaluatesPrecisionAndRecallAgainstLabeledData() {
        // Build known graph:
        //   ctlA → svcA → daoA → sqlA → table_a
        //   ctlB → svcB (isolated, no SQL path)
        String ctlA = m("CtlA");
        String svcA = m("SvcA");
        String daoA = m("DaoA");
        String ctlB = m("CtlB");
        String svcB = m("SvcB");

        FactStore store = InMemoryFactStore.defaults();
        store.insertAll(List.of(
                call(ctlA, svcA),
                call(svcA, daoA),
                call(ctlB, svcB)));

        ImpactAnalysisService service = ImpactAnalysisService.using(store);

        // Path 1: daoA → svcA → ctlA (TRUE positive — caller chain from changed daoA)
        ImpactPath truePath = new ImpactPath(List.of(daoA, svcA, ctlA));
        // Path 2: ctlB → svcB (FALSE positive candidate — unrelated chain)
        ImpactPath falseCandidate = new ImpactPath(List.of(ctlB, svcB));

        EvaluationSampleSet sampleSet = EvaluationSampleSet.define(
                "caller-chain-eval",
                "Tests whether starting from daoA correctly finds caller chain",
                List.of(new EvaluationSampleSet.LabeledPath(truePath, "ctl-through-svc-to-dao", "")),
                List.of(new EvaluationSampleSet.LabeledPath(falseCandidate, "unrelated-controller", "")));

        FastImpactReport impact = service.analyzeDiff(PROJ, SNAP, List.of(daoA), 10, 50);

        EvaluationSampleSet.EvaluationResult eval = service.evaluate(sampleSet, impact.paths());

        assertTrue(eval.truePositive() >= 1,
                "Should find the Ctl→Svc→Dao true positive path. "
                        + "Found: " + eval.totalReported() + " paths. " + eval.summary());
        assertEquals(0, eval.falsePositive(),
                "Should not report the unrelated CtlB→SvcB path as false positive. "
                        + eval.summary());
    }

    @Test
    void testRecommendationContextScoresByPathSymbolRisk() {
        String ctlA = m("RiskCtl");
        String svcA = m("RiskSvc");

        TestRecommendationContext ctx = TestRecommendationContext.of(
                java.util.Map.of(ctlA, 90, svcA, 10),
                java.util.Map.of(ctlA, "team-a", svcA, "team-b"),
                java.util.Map.of(ctlA, 25, svcA, 3));

        List<String> tests = List.of("testA", "testB");
        List<ImpactPath> paths = List.of(new ImpactPath(List.of(ctlA, svcA)));

        ImpactAnalysisService service = ImpactAnalysisService.defaults();
        List<String> prioritized = service.prioritizeTests(tests, paths, ctx);

        assertEquals(2, prioritized.size());
        // Both tests share the same paths — same score — insertion order preserved
        int totalRisk = ctx.riskScore(ctlA) + ctx.riskScore(svcA)
                + ctx.changeCount(ctlA) + ctx.changeCount(svcA);
        assertEquals(90 + 10 + 25 + 3, totalRisk);
    }

    @Test
    void emptyTestRecommendationContextReturnsOriginalOrder() {
        ImpactAnalysisService service = ImpactAnalysisService.defaults();
        List<String> tests = List.of("testA", "testB");
        List<ImpactPath> paths = List.of();

        List<String> result = service.prioritizeTests(tests, paths,
                TestRecommendationContext.empty());
        assertEquals(List.of("testA", "testB"), result);
    }
}
