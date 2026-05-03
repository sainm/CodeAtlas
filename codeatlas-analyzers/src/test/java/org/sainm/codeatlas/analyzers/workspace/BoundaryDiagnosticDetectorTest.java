package org.sainm.codeatlas.analyzers.workspace;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BoundaryDiagnosticDetectorTest {
    @TempDir
    Path tempDir;

    @Test
    void recordsNativeCobolAndJclBoundariesFromInventory() throws IOException {
        write("native/libpay.dll", new byte[] {1, 2, 3});
        write("native/payroll.c", "int calculate_pay(void) { return 1; }\n");
        write("cobol/PAYROLL.cbl", "       IDENTIFICATION DIVISION.\n       PROGRAM-ID. PAYROLL.\n");
        write("jcl/job.jcl", "//STEP01 EXEC PGM=PAYROLL\n");
        write("build/Makefile", "all:\n\tcc payroll.c\n");

        List<BoundaryDiagnostic> diagnostics = detect();

        assertHas(diagnostics, AnalysisBoundary.NATIVE, "native/libpay.dll");
        assertHas(diagnostics, AnalysisBoundary.C_BOUNDARY, "native/payroll.c");
        assertHas(diagnostics, AnalysisBoundary.COBOL_BOUNDARY, "cobol/PAYROLL.cbl");
        assertHas(diagnostics, AnalysisBoundary.JCL_BOUNDARY, "jcl/job.jcl");
        assertHas(diagnostics, AnalysisBoundary.EXTERNAL, "build/Makefile");
    }

    @Test
    void recordsSkippedFilesAsBlindSpotDiagnostics() throws IOException {
        write("bad.unknown", new byte[] {1, 2, 3});

        List<BoundaryDiagnostic> diagnostics = detect();

        assertHas(diagnostics, AnalysisBoundary.UNSUPPORTED, "bad.unknown");
    }

    private List<BoundaryDiagnostic> detect() throws IOException {
        WorkspaceInventory inventory = WorkspaceInventoryScanner.defaults()
                .scan(ImportRequest.localFolder("ws-boundary", tempDir, ImportMode.ASSISTED_IMPORT_REVIEW));
        return BoundaryDiagnosticDetector.defaults().detect(inventory);
    }

    private static void assertHas(List<BoundaryDiagnostic> diagnostics, AnalysisBoundary boundary, String path) {
        assertTrue(diagnostics.stream()
                .anyMatch(diagnostic -> diagnostic.boundary() == boundary && diagnostic.evidencePath().equals(path)),
                () -> "missing " + boundary + " at " + path + " in " + diagnostics);
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
