package org.sainm.codeatlas.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class TaiEOptionalWorkerSupervisorTest {
    @Test
    void returnsSupplementalSuccessWithoutBlockingFastAnalysis() {
        TaiEOptionalWorkerOutcome outcome = new TaiEOptionalWorkerSupervisor().runOptional(
            () -> new TaiEAnalysisImportResult(2, 1),
            Duration.ofSeconds(1)
        );

        assertEquals(WorkerTaskStatus.SUCCEEDED, outcome.status());
        assertEquals(2, outcome.nodeCount());
        assertEquals(1, outcome.factCount());
        assertTrue(outcome.supplemental());
    }

    @Test
    void degradesWhenTaiEWorkerFails() {
        TaiEOptionalWorkerOutcome outcome = new TaiEOptionalWorkerSupervisor().runOptional(
            () -> {
                throw new IllegalStateException("Tai-e jar missing");
            },
            Duration.ofSeconds(1)
        );

        assertEquals(WorkerTaskStatus.FAILED, outcome.status());
        assertTrue(outcome.degraded());
        assertEquals(0, outcome.nodeCount());
        assertEquals(0, outcome.factCount());
        assertTrue(outcome.message().contains("Tai-e jar missing"));
    }

    @Test
    void degradesWhenTaiEWorkerTimesOut() {
        TaiEOptionalWorkerOutcome outcome = new TaiEOptionalWorkerSupervisor().runOptional(
            () -> {
                Thread.sleep(500);
                return new TaiEAnalysisImportResult(1, 1);
            },
            Duration.ofMillis(20)
        );

        assertEquals(WorkerTaskStatus.TIMED_OUT, outcome.status());
        assertTrue(outcome.degraded());
        assertEquals(0, outcome.nodeCount());
        assertEquals(0, outcome.factCount());
        assertTrue(outcome.message().contains("timed out"));
    }
}
