package org.sainm.codeatlas.analyzers.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImportReviewReportGeneratorTest {
    @TempDir
    Path tempDir;

    @Test
    void generatesMixedWorkspaceImportReviewReport() throws IOException {
        write("app/build.gradle", "plugins { id 'java' }\n");
        write("app/src/main/java/App.java", "class App { public static void main(String[] args) {} }\n");
        write("app/src/main/webapp/index.jsp", "<form action=\"/save\"></form>\n");
        write("native/libpay.dll", new byte[] {1, 2, 3});
        write("native/payroll.c", "int calculate_pay(void) { return 1; }\n");
        write("cobol/PAYROLL.cbl", "       PROGRAM-ID. PAYROLL.\n");
        write("notes/bad.unknown", new byte[] {1, 2, 3});

        ImportReviewReport report = generate(ImportMode.ASSISTED_IMPORT_REVIEW);

        assertEquals("ws-review", report.workspaceId());
        assertEquals(7, report.overview().fileCount());
        assertTrue(report.overview().totalSizeBytes() > 0);
        assertEquals(3, report.overview().projectCandidateCount());
        assertTrue(report.requiresUserConfirmation());
        assertEquals(ProjectReviewStatus.READY, report.requireProject("app").status());
        assertEquals(ProjectReviewStatus.BOUNDARY_ONLY, report.requireProject("native").status());
        assertEquals(ProjectReviewStatus.BOUNDARY_ONLY, report.requireProject("cobol").status());
        assertTrue(report.capabilityCoverage().contains(CapabilityArea.JAVA_SOURCE));
        assertTrue(report.capabilityCoverage().contains(CapabilityArea.JSP_WEB));
        assertTrue(report.capabilityCoverage().contains(CapabilityArea.C_NATIVE));
        assertTrue(report.capabilityCoverage().contains(CapabilityArea.COBOL));
        assertTrue(report.blindSpots().stream().anyMatch(spot -> spot.evidencePath().equals("notes/bad.unknown")));
        assertTrue(report.recommendedAnalysisScopes().stream().anyMatch(scope -> scope.analyzerId().equals("java-source")));
        assertTrue(report.userConfirmationItems().stream().anyMatch(item -> item.contains("native")));
    }

    @Test
    void directImportDoesNotRequireReviewWhenWorkspaceIsReady() throws IOException {
        write("app/pom.xml", "<project />\n");
        write("app/src/main/java/App.java", "class App { public static void main(String[] args) {} }\n");

        ImportReviewReport report = generate(ImportMode.DIRECT_IMPORT);

        assertFalse(report.requiresUserConfirmation());
        assertEquals(ProjectReviewStatus.READY, report.requireProject("app").status());
    }

    @Test
    void assistedReviewRequiresConfirmationForBuildOnlyProjects() throws IOException {
        write("app/pom.xml", "<project />\n");

        ImportReviewReport report = generate(ImportMode.ASSISTED_IMPORT_REVIEW);

        assertEquals(ProjectReviewStatus.PARTIAL, report.requireProject("app").status());
        assertTrue(report.requiresUserConfirmation());
        assertTrue(report.userConfirmationItems().stream().anyMatch(item -> item.contains("app")));
    }

    @Test
    void bytecodeOnlyWorkspaceRecommendsBytecodeAnalysisScope() throws IOException {
        write("lib/app.jar", new byte[] {1, 2, 3});

        ImportReviewReport report = generate(ImportMode.DIRECT_IMPORT);

        assertEquals(ProjectReviewStatus.READY, report.requireProject(".").status());
        assertTrue(report.capabilityCoverage().contains(CapabilityArea.JAVA_BYTECODE));
        assertTrue(report.recommendedAnalysisScopes().stream()
                .anyMatch(scope -> scope.projectRoot().equals(".") && scope.analyzerId().equals("java-bytecode")));
    }

    @Test
    void assistedReviewRequiresConfirmationForUnsupportedBlindSpots() throws IOException {
        write("app/pom.xml", "<project />\n");
        write("app/src/main/java/App.java", "class App { public static void main(String[] args) {} }\n");
        write("notes/bad.unknown", new byte[] {1, 2, 3});

        ImportReviewReport report = generate(ImportMode.ASSISTED_IMPORT_REVIEW);

        assertTrue(report.blindSpots().stream().anyMatch(spot -> spot.evidencePath().equals("notes/bad.unknown")));
        assertTrue(report.requiresUserConfirmation());
        assertTrue(report.userConfirmationItems().stream().anyMatch(item -> item.contains("notes/bad.unknown")));
    }

    @Test
    void doesNotCountSkippedFilesAsAnalyzableCapability() throws IOException {
        write("app/src/main/java/Broken.java", new byte[] {(byte) 0xc3, 0x28});

        ImportReviewReport report = generate(ImportMode.DIRECT_IMPORT);

        assertFalse(report.capabilityCoverage().contains(CapabilityArea.JAVA_SOURCE));
        assertTrue(report.capabilityCoverage().contains(CapabilityArea.UNSUPPORTED));
    }

    @Test
    void doesNotRecommendRootJavaScopeFromNestedProjectFiles() throws IOException {
        write("settings.gradle", "include 'app'\n");
        write("app/build.gradle", "plugins { id 'java' }\n");
        write("app/src/main/java/App.java", "class App {}\n");

        ImportReviewReport report = generate(ImportMode.ASSISTED_IMPORT_REVIEW);

        assertTrue(report.projects().stream().anyMatch(project -> project.rootPath().equals(".")));
        assertTrue(report.recommendedAnalysisScopes().stream()
                .anyMatch(scope -> scope.projectRoot().equals("app") && scope.analyzerId().equals("java-source")));
        assertFalse(report.recommendedAnalysisScopes().stream()
                .anyMatch(scope -> scope.projectRoot().equals(".") && scope.analyzerId().equals("java-source")));
    }

    @Test
    void doesNotRecommendJavaScopeFromSkippedSourceFiles() throws IOException {
        write("app/pom.xml", "<project />\n");
        write("app/src/main/java/Broken.java", new byte[] {(byte) 0xc3, 0x28});

        ImportReviewReport report = generate(ImportMode.ASSISTED_IMPORT_REVIEW);

        assertEquals(ProjectReviewStatus.PARTIAL, report.requireProject("app").status());
        assertFalse(report.recommendedAnalysisScopes().stream()
                .anyMatch(scope -> scope.projectRoot().equals("app") && scope.analyzerId().equals("java-source")));
    }

    @Test
    void assignsNestedEvidenceToDeepestProjectCandidate() throws IOException {
        write("settings.gradle", "include 'app'\n");
        write("app/build.gradle", "plugins { id 'java' }\n");
        write("app/src/main/java/App.java", "class App { public static void main(String[] args) {} }\n");

        ImportReviewReport report = generate(ImportMode.ASSISTED_IMPORT_REVIEW);

        assertTrue(report.requireProject("app").entrypointEvidencePaths()
                .contains("app/src/main/java/App.java"));
        assertFalse(report.requireProject(".").entrypointEvidencePaths()
                .contains("app/src/main/java/App.java"));
    }

    @Test
    void preservesEntrypointsWhenNoLayoutCandidateExists() throws IOException {
        write("run.sh", "java com.acme.Main\n");

        ImportReviewReport report = generate(ImportMode.ASSISTED_IMPORT_REVIEW);

        assertEquals(ProjectReviewStatus.PARTIAL, report.requireProject(".").status());
        assertTrue(report.requireProject(".").entrypointEvidencePaths().contains("run.sh"));
        assertTrue(report.requiresUserConfirmation());
    }

    private ImportReviewReport generate(ImportMode mode) throws IOException {
        WorkspaceInventory inventory = WorkspaceInventoryScanner.defaults()
                .scan(ImportRequest.localFolder("ws-review", tempDir, mode));
        WorkspaceLayoutProfile layoutProfile = WorkspaceLayoutDetector.defaults().detect(inventory);
        return ImportReviewReportGenerator.defaults().generate(inventory, layoutProfile);
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
