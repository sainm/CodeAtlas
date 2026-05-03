package org.sainm.codeatlas.analyzers.workspace;

import java.util.ArrayList;
import java.util.List;

public final class AnalyzerTaskSupervisor {
    private AnalyzerTaskSupervisor() {
    }

    public static AnalyzerTaskSupervisor defaults() {
        return new AnalyzerTaskSupervisor();
    }

    public AnalyzerTaskExecutionPlan prepare(
            AnalyzerTaskGraph graph,
            AnalyzerTaskExecutionPolicy policy) {
        if (graph == null) {
            throw new IllegalArgumentException("graph is required");
        }
        if (policy == null) {
            throw new IllegalArgumentException("policy is required");
        }
        List<AnalyzerTaskExecution> executions = graph.tasks().stream()
                .map(task -> new AnalyzerTaskExecution(
                        task,
                        AnalyzerTaskStatus.PLANNED,
                        0,
                        policy.timeoutMillis(),
                        ""))
                .toList();
        return new AnalyzerTaskExecutionPlan(graph.analysisRunId(), policy, executions);
    }

    public AnalyzerTaskExecutionPlan fail(
            AnalyzerTaskExecutionPlan plan,
            String analyzerId,
            String projectRoot,
            String error) {
        return transition(plan, analyzerId, projectRoot, error, false);
    }

    public AnalyzerTaskExecutionPlan timeout(
            AnalyzerTaskExecutionPlan plan,
            String analyzerId,
            String projectRoot) {
        String error = "task exceeded timeout: " + plan.policy().timeoutMillis() + "ms";
        return transition(plan, analyzerId, projectRoot, error, true);
    }

    public AnalyzerTaskExecutionPlan cancel(
            AnalyzerTaskExecutionPlan plan,
            String analyzerId,
            String projectRoot,
            String reason) {
        return replace(plan, analyzerId, projectRoot, execution -> new AnalyzerTaskExecution(
                execution.task(),
                AnalyzerTaskStatus.CANCELLED,
                execution.attempt(),
                execution.timeoutMillis(),
                reason));
    }

    private static AnalyzerTaskExecutionPlan transition(
            AnalyzerTaskExecutionPlan plan,
            String analyzerId,
            String projectRoot,
            String error,
            boolean timeout) {
        return replace(plan, analyzerId, projectRoot, execution -> {
            int nextAttempt = execution.attempt() + 1;
            AnalyzerTaskStatus status;
            if (nextAttempt <= plan.policy().maxRetries()) {
                status = AnalyzerTaskStatus.RETRY_WAITING;
            } else {
                status = timeout ? AnalyzerTaskStatus.TIMED_OUT : AnalyzerTaskStatus.FAILED;
            }
            return new AnalyzerTaskExecution(
                    execution.task(),
                    status,
                    nextAttempt,
                    execution.timeoutMillis(),
                    error);
        });
    }

    private static AnalyzerTaskExecutionPlan replace(
            AnalyzerTaskExecutionPlan plan,
            String analyzerId,
            String projectRoot,
            ExecutionTransition transition) {
        if (plan == null) {
            throw new IllegalArgumentException("plan is required");
        }
        String normalizedRoot = ProjectLayoutCandidate.normalizeRoot(projectRoot);
        List<AnalyzerTaskExecution> executions = new ArrayList<>();
        boolean found = false;
        for (AnalyzerTaskExecution execution : plan.executions()) {
            if (execution.analyzerId().equals(analyzerId) && execution.projectRoot().equals(normalizedRoot)) {
                executions.add(transition.apply(execution));
                found = true;
            } else {
                executions.add(execution);
            }
        }
        if (!found) {
            throw new IllegalArgumentException("No analyzer task execution for " + analyzerId + ":" + projectRoot);
        }
        return new AnalyzerTaskExecutionPlan(plan.analysisRunId(), plan.policy(), executions);
    }

    private interface ExecutionTransition {
        AnalyzerTaskExecution apply(AnalyzerTaskExecution execution);
    }
}
