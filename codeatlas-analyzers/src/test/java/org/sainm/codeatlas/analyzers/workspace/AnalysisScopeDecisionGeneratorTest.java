package org.sainm.codeatlas.analyzers.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalysisScopeDecisionGeneratorTest {
    @TempDir
    Path tempDir;

    @Test
    void recordsUserConfirmedScopeDecisionAndAuditTrail() throws IOException {
        write("app/build.gradle", "plugins { id 'java' }\n");
        write("app/src/main/java/App.java", "class App {}\n");
        write("app/src/main/webapp/index.jsp", "<html></html>\n");
        write("native/libpay.dll", new byte[] {1, 2, 3});
        write("cobol/PAYROLL.cbl", "       PROGRAM-ID. PAYROLL.\n");

        ImportReviewReport report = generateReport();
        AnalysisScopeDecisionRequest request = new AnalysisScopeDecisionRequest(
                List.of("app"),
                List.of("native"),
                List.of("shared-lib"),
                List.of("lib/ojdbc.jar"),
                List.of("app/src/main/java"),
                List.of("app/WEB-INF/lib"),
                List.of("app/src/main/webapp"),
                List.of("scripts"),
                List.of("tmp"));

        AnalysisScopeDecision decision = AnalysisScopeDecisionGenerator.defaults().confirm(report, request);
        ImportReviewReport finalReport = report.withAnalysisScopeDecision(decision);

        assertEquals(List.of("app"), decision.includedProjectRoots());
        assertEquals(List.of("native"), decision.excludedProjectRoots());
        assertEquals(List.of("shared-lib"), decision.sharedLibraryRoots());
        assertEquals(List.of("lib/ojdbc.jar"), decision.additionalDependencyPaths());
        assertEquals(List.of("app/src/main/java"), decision.sourceRoots());
        assertEquals(List.of("app/WEB-INF/lib"), decision.libraryRoots());
        assertEquals(List.of("app/src/main/webapp"), decision.webRoots());
        assertEquals(List.of("scripts"), decision.scriptRoots());
        assertEquals(List.of("tmp"), decision.ignoredDirectories());
        assertEquals(decision, finalReport.analysisScopeDecision());
        assertAudit(decision, "app", AnalysisScopeDisposition.INCLUDED);
        assertAudit(decision, "native", AnalysisScopeDisposition.EXCLUDED);
        assertAudit(decision, "cobol", AnalysisScopeDisposition.UNANALYZABLE);
        assertTrue(decision.auditEntries().stream()
                .anyMatch(entry -> entry.scopePath().equals("shared-lib")
                        && entry.disposition() == AnalysisScopeDisposition.SHARED_LIBRARY));
    }

    @Test
    void recordsUnconfirmedPartialProjectsAsDegraded() throws IOException {
        write("web/src/main/webapp/index.jsp", "<form action=\"/save\"></form>\n");

        ImportReviewReport report = generateReport();

        AnalysisScopeDecision decision = AnalysisScopeDecisionGenerator.defaults()
                .confirm(report, AnalysisScopeDecisionRequest.empty());

        assertEquals(ProjectReviewStatus.PARTIAL, report.requireProject("web").status());
        assertAudit(decision, "web", AnalysisScopeDisposition.DEGRADED);
    }

    @Test
    void sharedLibrarySelectionOverridesDefaultReadyProjectInclusion() throws IOException {
        write("lib/build.gradle", "plugins { id 'java' }\n");
        write("lib/src/main/java/Library.java", "class Library {}\n");

        ImportReviewReport report = generateReport();
        AnalysisScopeDecision decision = AnalysisScopeDecisionGenerator.defaults().confirm(
                report,
                new AnalysisScopeDecisionRequest(
                        List.of(),
                        List.of(),
                        List.of("lib"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()));

        assertEquals(ProjectReviewStatus.READY, report.requireProject("lib").status());
        assertAudit(decision, "lib", AnalysisScopeDisposition.SHARED_LIBRARY);
        assertTrue(decision.auditEntries().stream()
                .noneMatch(entry -> entry.scopePath().equals("lib")
                        && entry.disposition() == AnalysisScopeDisposition.INCLUDED));
    }

    private ImportReviewReport generateReport() throws IOException {
        WorkspaceInventory inventory = WorkspaceInventoryScanner.defaults()
                .scan(ImportRequest.localFolder("ws-scope", tempDir, ImportMode.ASSISTED_IMPORT_REVIEW));
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
        write(relativePath, content.getBytes(StandardCharsets.UTF_8));
    }

    private void write(String relativePath, byte[] content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.write(file, content);
    }
}
