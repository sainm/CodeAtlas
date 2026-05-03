package org.sainm.codeatlas.analyzers.workspace;

import java.util.Comparator;
import java.util.List;

public record AnalyzerTaskGraph(
        String analysisRunId,
        String snapshotId,
        List<AnalyzerTask> tasks) {
    public AnalyzerTaskGraph {
        if (analysisRunId == null || analysisRunId.isBlank()) {
            throw new IllegalArgumentException("analysisRunId is required");
        }
        snapshotId = snapshotId == null ? "" : snapshotId;
        tasks = List.copyOf(tasks == null ? List.of() : tasks).stream()
                .sorted(Comparator.comparingInt(AnalyzerTask::priority)
                        .thenComparing(task -> task.queue().name())
                        .thenComparing(AnalyzerTask::analyzerId)
                        .thenComparing(AnalyzerTask::projectRoot))
                .toList();
    }

    public AnalyzerTask requireTask(String analyzerId, String projectRoot) {
        String normalizedRoot = ProjectLayoutCandidate.normalizeRoot(projectRoot);
        return tasks.stream()
                .filter(task -> task.analyzerId().equals(analyzerId) && task.projectRoot().equals(normalizedRoot))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No analyzer task for " + analyzerId + ":" + projectRoot));
    }

    public boolean hasTask(String analyzerId, String projectRoot) {
        String normalizedRoot = ProjectLayoutCandidate.normalizeRoot(projectRoot);
        return tasks.stream()
                .anyMatch(task -> task.analyzerId().equals(analyzerId) && task.projectRoot().equals(normalizedRoot));
    }

    public List<AnalyzerTask> fastTasks() {
        return tasks.stream()
                .filter(task -> task.queue() == AnalyzerTaskQueue.FAST_REPORT)
                .toList();
    }

    public List<AnalyzerTask> backgroundTasks() {
        return tasks.stream()
                .filter(task -> task.queue() == AnalyzerTaskQueue.BACKGROUND_DEEP)
                .toList();
    }
}
