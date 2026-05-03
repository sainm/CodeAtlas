package org.sainm.codeatlas.analyzers.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class AnalyzerTaskSupervisorTest {
    @Test
    void appliesTimeoutRetryCancellationAndFailureIsolationPerTask() {
        AnalyzerTask javaTask = new AnalyzerTask(
                "java-source",
                "app",
                "java-source:app",
                AnalyzerTaskQueue.FAST_REPORT,
                AnalyzerTaskReason.CHANGED_SCOPE,
                0);
        AnalyzerTask jspTask = new AnalyzerTask(
                "jsp-web",
                "app",
                "jsp-web:app",
                AnalyzerTaskQueue.BACKGROUND_DEEP,
                AnalyzerTaskReason.CACHED_BACKGROUND_REFRESH,
                10);
        AnalyzerTaskGraph graph = new AnalyzerTaskGraph("analysis-ws", "snap-1", List.of(javaTask, jspTask));
        AnalyzerTaskSupervisor supervisor = AnalyzerTaskSupervisor.defaults();
        AnalyzerTaskExecutionPlan plan = supervisor.prepare(
                graph,
                new AnalyzerTaskExecutionPolicy(30_000, 1));

        AnalyzerTaskExecutionPlan failedOnce = supervisor.fail(plan, "java-source", "app", "compiler error");

        assertEquals(AnalyzerTaskStatus.RETRY_WAITING, failedOnce.requireExecution("java-source", "app").status());
        assertEquals(1, failedOnce.requireExecution("java-source", "app").attempt());
        assertEquals("compiler error", failedOnce.requireExecution("java-source", "app").lastError());
        assertEquals(AnalyzerTaskStatus.PLANNED, failedOnce.requireExecution("jsp-web", "app").status());

        AnalyzerTaskExecutionPlan failedTwice = supervisor.fail(failedOnce, "java-source", "app", "still broken");

        assertEquals(AnalyzerTaskStatus.FAILED, failedTwice.requireExecution("java-source", "app").status());
        assertEquals(2, failedTwice.requireExecution("java-source", "app").attempt());
        assertEquals(AnalyzerTaskStatus.PLANNED, failedTwice.requireExecution("jsp-web", "app").status());

        AnalyzerTaskExecutionPlan cancelled = supervisor.cancel(failedTwice, "jsp-web", "app", "user cancelled");

        assertEquals(AnalyzerTaskStatus.CANCELLED, cancelled.requireExecution("jsp-web", "app").status());
        assertEquals("user cancelled", cancelled.requireExecution("jsp-web", "app").lastError());
    }

    @Test
    void retriesTimedOutTasksWithinPolicy() {
        AnalyzerTask task = new AnalyzerTask(
                "java-source",
                "app",
                "java-source:app",
                AnalyzerTaskQueue.FAST_REPORT,
                AnalyzerTaskReason.CACHE_MISS,
                1);
        AnalyzerTaskGraph graph = new AnalyzerTaskGraph("analysis-ws", "snap-1", List.of(task));
        AnalyzerTaskExecutionPlan plan = AnalyzerTaskSupervisor.defaults().prepare(
                graph,
                new AnalyzerTaskExecutionPolicy(500, 2));

        AnalyzerTaskExecutionPlan timedOut = AnalyzerTaskSupervisor.defaults()
                .timeout(plan, "java-source", "app");

        assertEquals(AnalyzerTaskStatus.RETRY_WAITING, timedOut.requireExecution("java-source", "app").status());
        assertEquals(1, timedOut.requireExecution("java-source", "app").attempt());
        assertEquals("task exceeded timeout: 500ms", timedOut.requireExecution("java-source", "app").lastError());
    }
}
