package org.sainm.codeatlas.analyzers.workspace;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AnalysisPlanner {
    private AnalysisPlanner() {
    }

    public static AnalysisPlanner defaults() {
        return new AnalysisPlanner();
    }

    public AnalyzerTaskGraph plan(
            ImportReviewReport report,
            GitDiffSummary diff,
            ExistingSnapshotSummary snapshot) {
        if (report == null) {
            throw new IllegalArgumentException("report is required");
        }
        GitDiffSummary normalizedDiff = diff == null ? new GitDiffSummary(List.of()) : diff;
        ExistingSnapshotSummary normalizedSnapshot = snapshot == null
                ? new ExistingSnapshotSummary("", List.of())
                : snapshot;
        Set<String> includedProjects = new HashSet<>(report.analysisScopeDecision().includedProjectRoots());
        List<AnalyzerTask> tasks = new ArrayList<>();
        for (RecommendedAnalysisScope scope : report.recommendedAnalysisScopes()) {
            if (!includedProjects.contains(scope.projectRoot())) {
                continue;
            }
            tasks.add(toTask(scope, normalizedDiff, normalizedSnapshot));
        }
        return new AnalyzerTaskGraph(
                "analysis-" + report.workspaceId(),
                normalizedSnapshot.snapshotId(),
                tasks);
    }

    private static AnalyzerTask toTask(
            RecommendedAnalysisScope scope,
            GitDiffSummary diff,
            ExistingSnapshotSummary snapshot) {
        String scopeKey = scope.analyzerId() + ":" + scope.projectRoot();
        boolean changed = diff.changedPaths().stream()
                .anyMatch(path -> isUnderProject(path, scope.projectRoot()) && matchesAnalyzer(scope.analyzerId(), path));
        if (changed) {
            return new AnalyzerTask(
                    scope.analyzerId(),
                    scope.projectRoot(),
                    scopeKey,
                    AnalyzerTaskQueue.FAST_REPORT,
                    AnalyzerTaskReason.CHANGED_SCOPE,
                    0);
        }
        if (!snapshot.hasCachedScope(scopeKey)) {
            return new AnalyzerTask(
                    scope.analyzerId(),
                    scope.projectRoot(),
                    scopeKey,
                    AnalyzerTaskQueue.FAST_REPORT,
                    AnalyzerTaskReason.CACHE_MISS,
                    1);
        }
        return new AnalyzerTask(
                scope.analyzerId(),
                scope.projectRoot(),
                scopeKey,
                AnalyzerTaskQueue.BACKGROUND_DEEP,
                AnalyzerTaskReason.CACHED_BACKGROUND_REFRESH,
                10);
    }

    private static boolean matchesAnalyzer(String analyzerId, String path) {
        String lower = path.toLowerCase();
        return switch (analyzerId) {
            case "java-source" -> lower.endsWith(".java");
            case "jsp-web" -> lower.endsWith(".jsp") || lower.endsWith(".jspx")
                    || lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".js");
            case "boundary" -> true;
            default -> true;
        };
    }

    private static boolean isUnderProject(String path, String projectRoot) {
        String normalizedRoot = ProjectLayoutCandidate.normalizeRoot(projectRoot);
        return normalizedRoot.equals(".") || path.equals(normalizedRoot) || path.startsWith(normalizedRoot + "/");
    }
}
