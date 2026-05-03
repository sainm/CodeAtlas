package org.sainm.codeatlas.analyzers.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalysisPlannerTest {
    @TempDir
    Path tempDir;

    @Test
    void convertsReviewDiffAndSelectedScopesToAnalyzerTaskGraph() throws IOException {
        write("app/build.gradle", "plugins { id 'java' }\n");
        write("app/src/main/java/App.java", "class App {}\n");
        write("app/src/main/webapp/index.jsp", "<html></html>\n");
        write("native/payroll.c", "int main(void) { return 0; }\n");
        ImportReviewReport report = generateReport();
        AnalysisScopeDecision decision = AnalysisScopeDecisionGenerator.defaults().confirm(
                report,
                new AnalysisScopeDecisionRequest(
                        List.of("app"),
                        List.of("native"),
                        List.of(),
                        List.of(),
                        List.of("app/src/main/java"),
                        List.of(),
                        List.of("app/src/main/webapp"),
                        List.of(),
                        List.of()));

        AnalyzerTaskGraph graph = AnalysisPlanner.defaults().plan(
                report.withAnalysisScopeDecision(decision),
                new GitDiffSummary(List.of("app/src/main/java/App.java")),
                new ExistingSnapshotSummary("snap-1", List.of("jsp-web:app")));

        AnalyzerTask javaTask = graph.requireTask("java-source", "app");
        AnalyzerTask jspTask = graph.requireTask("jsp-web", "app");

        assertEquals("analysis-ws-plan", graph.analysisRunId());
        assertEquals(AnalyzerTaskQueue.FAST_REPORT, javaTask.queue());
        assertEquals(AnalyzerTaskReason.CHANGED_SCOPE, javaTask.reason());
        assertEquals(0, javaTask.priority());
        assertFalse(javaTask.executesProjectCode());
        assertEquals(AnalyzerTaskQueue.BACKGROUND_DEEP, jspTask.queue());
        assertEquals(AnalyzerTaskReason.CACHED_BACKGROUND_REFRESH, jspTask.reason());
        assertFalse(jspTask.executesProjectCode());
        assertFalse(graph.tasks().stream().anyMatch(task -> task.analyzerId().equals("build-script-exec")));
        assertFalse(graph.hasTask("boundary", "native"));
        assertTrue(graph.fastTasks().stream().allMatch(task -> task.queue() == AnalyzerTaskQueue.FAST_REPORT));
        assertTrue(graph.backgroundTasks().stream().allMatch(task -> task.queue() == AnalyzerTaskQueue.BACKGROUND_DEEP));
    }

    @Test
    void plansPartialWebProjectWhenUserIncludesIt() throws IOException {
        write("app/src/main/webapp/index.jsp", "<form action=\"/save\"></form>\n");
        ImportReviewReport report = generateReport();
        AnalysisScopeDecision decision = AnalysisScopeDecisionGenerator.defaults().confirm(
                report,
                new AnalysisScopeDecisionRequest(
                        List.of("app"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("app/src/main/webapp"),
                        List.of(),
                        List.of()));

        AnalyzerTaskGraph graph = AnalysisPlanner.defaults().plan(
                report.withAnalysisScopeDecision(decision),
                new GitDiffSummary(List.of()),
                new ExistingSnapshotSummary("snap-1", List.of()));

        AnalyzerTask jspTask = graph.requireTask("jsp-web", "app");

        assertEquals(ProjectReviewStatus.PARTIAL, report.requireProject("app").status());
        assertEquals(AnalyzerTaskQueue.FAST_REPORT, jspTask.queue());
        assertEquals(AnalyzerTaskReason.CACHE_MISS, jspTask.reason());
        assertFalse(graph.hasTask("java-source", "app"));
    }

    @Test
    void plansReadyProjectsThatWereImplicitlyIncludedByDefaultConfirmation() throws IOException {
        write("app/build.gradle", "plugins { id 'java' }\n");
        write("app/src/main/java/App.java", "class App {}\n");
        ImportReviewReport report = generateReport();
        AnalysisScopeDecision decision = AnalysisScopeDecisionGenerator.defaults()
                .confirm(report, AnalysisScopeDecisionRequest.empty());

        AnalyzerTaskGraph graph = AnalysisPlanner.defaults().plan(
                report.withAnalysisScopeDecision(decision),
                new GitDiffSummary(List.of("app/src/main/java/App.java")),
                new ExistingSnapshotSummary("snap-1", List.of()));

        assertTrue(decision.includedProjectRoots().isEmpty());
        assertTrue(decision.auditEntries().stream()
                .anyMatch(entry -> entry.scopePath().equals("app")
                        && entry.disposition() == AnalysisScopeDisposition.INCLUDED));
        assertTrue(graph.hasTask("java-source", "app"));
    }

    @Test
    void doesNotPlanScopesOverriddenByExclusionOrSharedLibrarySelection() throws IOException {
        write("app/build.gradle", "plugins { id 'java' }\n");
        write("app/src/main/java/App.java", "class App {}\n");
        write("lib/build.gradle", "plugins { id 'java' }\n");
        write("lib/src/main/java/Library.java", "class Library {}\n");
        ImportReviewReport report = generateReport();
        AnalysisScopeDecision decision = AnalysisScopeDecisionGenerator.defaults().confirm(
                report,
                new AnalysisScopeDecisionRequest(
                        List.of("app", "lib"),
                        List.of("app"),
                        List.of("lib"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()));

        AnalyzerTaskGraph graph = AnalysisPlanner.defaults().plan(
                report.withAnalysisScopeDecision(decision),
                new GitDiffSummary(List.of("app/src/main/java/App.java", "lib/src/main/java/Library.java")),
                new ExistingSnapshotSummary("snap-1", List.of()));

        assertAudit(decision, "app", AnalysisScopeDisposition.EXCLUDED);
        assertAudit(decision, "lib", AnalysisScopeDisposition.SHARED_LIBRARY);
        assertFalse(graph.hasTask("java-source", "app"));
        assertFalse(graph.hasTask("java-source", "lib"));
    }

    @Test
    void plansBytecodeOnlyProjectsThatWereImplicitlyIncluded() throws IOException {
        write("lib/app.jar", new byte[] {1, 2, 3});
        ImportReviewReport report = generateReport();
        AnalysisScopeDecision decision = AnalysisScopeDecisionGenerator.defaults()
                .confirm(report, AnalysisScopeDecisionRequest.empty());

        AnalyzerTaskGraph graph = AnalysisPlanner.defaults().plan(
                report.withAnalysisScopeDecision(decision),
                new GitDiffSummary(List.of("lib/app.jar")),
                new ExistingSnapshotSummary("snap-1", List.of()));

        assertTrue(report.recommendedAnalysisScopes().stream()
                .anyMatch(scope -> scope.projectRoot().equals(".") && scope.analyzerId().equals("java-bytecode")));
        assertTrue(graph.hasTask("java-bytecode", "."));
        assertEquals(AnalyzerTaskReason.CHANGED_SCOPE, graph.requireTask("java-bytecode", ".").reason());
    }

    @Test
    void plansReadyDirectImportsWithoutExplicitScopeDecision() throws IOException {
        write("app/src/main/java/App.java", "class App {}\n");
        ImportReviewReport report = generateReport(ImportMode.DIRECT_IMPORT);

        AnalyzerTaskGraph graph = AnalysisPlanner.defaults().plan(
                report,
                new GitDiffSummary(List.of("app/src/main/java/App.java")),
                new ExistingSnapshotSummary("snap-1", List.of()));

        assertFalse(report.requiresUserConfirmation());
        assertTrue(graph.hasTask("java-source", "app"));
    }

    @Test
    void plansDirectImportWebScopesWithoutExplicitScopeDecision() throws IOException {
        write("app/src/main/webapp/index.jsp", "<html></html>\n");
        ImportReviewReport report = generateReport(ImportMode.DIRECT_IMPORT);

        AnalyzerTaskGraph graph = AnalysisPlanner.defaults().plan(
                report,
                new GitDiffSummary(List.of("app/src/main/webapp/index.jsp")),
                new ExistingSnapshotSummary("snap-1", List.of()));

        assertFalse(report.requiresUserConfirmation());
        assertEquals(ProjectReviewStatus.PARTIAL, report.requireProject("app").status());
        assertTrue(report.recommendedAnalysisScopes().stream()
                .anyMatch(scope -> scope.projectRoot().equals("app") && scope.analyzerId().equals("jsp-web")));
        assertTrue(graph.hasTask("jsp-web", "app"));
    }

    private ImportReviewReport generateReport() throws IOException {
        return generateReport(ImportMode.ASSISTED_IMPORT_REVIEW);
    }

    private ImportReviewReport generateReport(ImportMode mode) throws IOException {
        WorkspaceInventory inventory = WorkspaceInventoryScanner.defaults()
                .scan(ImportRequest.localFolder("ws-plan", tempDir, mode));
        WorkspaceLayoutProfile layoutProfile = WorkspaceLayoutDetector.defaults().detect(inventory);
        return ImportReviewReportGenerator.defaults().generate(inventory, layoutProfile);
    }

    private static void assertAudit(
            AnalysisScopeDecision decision,
            String scopePath,
            AnalysisScopeDisposition disposition) {
        assertTrue(decision.auditEntries().stream()
                .anyMatch(entry -> entry.scopePath().equals(scopePath) && entry.disposition() == disposition));
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private void write(String relativePath, byte[] content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.write(file, content);
    }
}
