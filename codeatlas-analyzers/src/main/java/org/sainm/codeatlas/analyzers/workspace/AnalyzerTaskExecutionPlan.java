package org.sainm.codeatlas.analyzers.workspace;

import java.util.List;

public record AnalyzerTaskExecutionPlan(
        String analysisRunId,
        AnalyzerTaskExecutionPolicy policy,
        List<AnalyzerTaskExecution> executions) {
    public AnalyzerTaskExecutionPlan {
        if (analysisRunId == null || analysisRunId.isBlank()) {
            throw new IllegalArgumentException("analysisRunId is required");
        }
        if (policy == null) {
            throw new IllegalArgumentException("policy is required");
        }
        executions = List.copyOf(executions == null ? List.of() : executions);
    }

    public AnalyzerTaskExecution requireExecution(String analyzerId, String projectRoot) {
        String normalizedRoot = ProjectLayoutCandidate.normalizeRoot(projectRoot);
        return executions.stream()
                .filter(execution -> execution.analyzerId().equals(analyzerId)
                        && execution.projectRoot().equals(normalizedRoot))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No analyzer task execution for " + analyzerId + ":" + projectRoot));
    }
}
