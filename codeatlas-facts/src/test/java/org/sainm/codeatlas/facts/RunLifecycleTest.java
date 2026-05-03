package org.sainm.codeatlas.facts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RunLifecycleTest {
    @Test
    void advancesAnalysisRunThroughCommittedLifecycle() {
        AnalysisRun planned = AnalysisRun.planned("analysis-1", "shop", "snapshot-2");

        AnalysisRun running = planned.start();
        AnalysisRun staged = running.stage();
        AnalysisRun committed = staged.commit();

        assertEquals(RunStatus.PLANNED, planned.status());
        assertEquals(RunStatus.RUNNING, running.status());
        assertEquals(RunStatus.STAGED, staged.status());
        assertEquals(RunStatus.COMMITTED, committed.status());
        assertEquals("analysis-1", committed.analysisRunId());
        assertEquals("shop", committed.projectId());
        assertEquals("snapshot-2", committed.snapshotId());
    }

    @Test
    void rejectsInvalidAnalysisRunTransitions() {
        AnalysisRun planned = AnalysisRun.planned("analysis-1", "shop", "snapshot-2");

        assertThrows(IllegalStateException.class, planned::commit);
        assertThrows(IllegalStateException.class, planned::rollback);
        assertThrows(IllegalStateException.class, () -> planned.fail().commit());
        assertThrows(IllegalStateException.class, () -> planned.start().stage().commit().fail());
        assertThrows(IllegalArgumentException.class,
                () -> new AnalysisRun("analysis-1", "shop", "snapshot-2", RunStatus.SKIPPED));
    }

    @Test
    void advancesScopeRunThroughCommittedLifecycle() {
        ScopeRun planned = ScopeRun.planned(
                "scope-run-1",
                "analysis-1",
                "spoon",
                "src/main/java/com/foo/A.java",
                RelationFamily.CALL);

        ScopeRun committed = planned.start().stage().commit();

        assertEquals(RunStatus.COMMITTED, committed.status());
        assertEquals("scope-run-1", committed.scopeRunId());
        assertEquals("analysis-1", committed.analysisRunId());
        assertEquals("spoon", committed.analyzerId());
        assertEquals("src/main/java/com/foo/A.java", committed.scopeKey());
        assertEquals(RelationFamily.CALL, committed.relationFamily());
    }

    @Test
    void supportsScopeRunFailureAndSkipBoundaries() {
        ScopeRun planned = ScopeRun.planned(
                "scope-run-1",
                "analysis-1",
                "spoon",
                "src/main/java/com/foo/A.java",
                RelationFamily.CALL);

        assertEquals(RunStatus.SKIPPED, planned.skip().status());
        assertEquals(RunStatus.FAILED, planned.start().fail().status());
        assertThrows(IllegalStateException.class, planned.skip()::start);
        assertThrows(IllegalStateException.class, () -> planned.start().fail().stage());
        assertThrows(IllegalArgumentException.class,
                () -> new ScopeRun(
                        "scope-run-1",
                        "analysis-1",
                        "spoon",
                        "src/main/java/com/foo/A.java",
                        RelationFamily.CALL,
                        RunStatus.ROLLED_BACK));
    }
}
