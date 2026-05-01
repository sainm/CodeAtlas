package org.sainm.codeatlas.graph.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FfmActivationPolicyTest {
    @Test
    void doesNotRecommendFfmForSmallOrFastGraphs() {
        FfmActivationPolicy policy = new FfmActivationPolicy(1_000_000, 250.0d, 512L * 1024L * 1024L);

        FfmActivationDecision small = policy.evaluate(new GraphScaleMetrics(100_000, 320.0d, 800L * 1024L * 1024L));
        FfmActivationDecision fast = policy.evaluate(new GraphScaleMetrics(2_000_000, 20.0d, 800L * 1024L * 1024L));

        assertFalse(small.recommended());
        assertTrue(small.reasons().contains("edgeCount below threshold"));
        assertFalse(fast.recommended());
        assertTrue(fast.reasons().contains("p95Millis below threshold"));
    }

    @Test
    void recommendsFfmOnlyWhenScaleLatencyAndHeapPressureAreAllHigh() {
        FfmActivationPolicy policy = FfmActivationPolicy.defaultPolicy();

        FfmActivationDecision decision = policy.evaluate(new GraphScaleMetrics(
            policy.minEdgeCount(),
            policy.minP95Millis(),
            policy.minHeapBytes()
        ));

        assertTrue(decision.recommended());
        assertEquals("FFM candidate: graph scale, P95 latency, and heap estimate meet thresholds", decision.summary());
    }
}
