package org.sainm.codeatlas.analyzers.workspace;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DirectImportGateTest {
    @TempDir
    Path tempDir;

    @Test
    void blocksDirectImportForClearlyNonJavaWorkspace() throws IOException {
        write("native/payroll.c", "int main(void) { return 0; }\n");

        GateFixture fixture = fixture(ImportRequest.localFolder("ws-native", tempDir, ImportMode.DIRECT_IMPORT));

        ImportGateDecision decision = DirectImportGate.defaults()
                .evaluate(fixture.request(), fixture.inventory(), fixture.report());

        assertFalse(decision.allowed());
        assertTrue(decision.hasBlockingIssue("NON_JAVA_WORKSPACE"));
    }

    @Test
    void warnsDirectImportForLegacySourceOnlyJavaWorkspace() throws IOException {
        write("src/App.java", "class App {}\n");

        GateFixture fixture = fixture(ImportRequest.localFolder("ws-legacy", tempDir, ImportMode.DIRECT_IMPORT));

        ImportGateDecision decision = DirectImportGate.defaults()
                .evaluate(fixture.request(), fixture.inventory(), fixture.report());

        assertTrue(decision.allowed());
        assertTrue(decision.hasWarning("LEGACY_JAVA_LAYOUT"));
    }

    @Test
    void blocksDirectImportForUnsafeUploadedArchiveType() throws IOException {
        Path archive = tempDir.resolve("workspace.exe");
        Files.write(archive, new byte[] {1, 2, 3});
        Path extractedRoot = Files.createDirectory(tempDir.resolve("extracted"));
        Files.writeString(extractedRoot.resolve("pom.xml"), "<project />\n", StandardCharsets.UTF_8);
        ImportRequest request = ImportRequest.uploadedArchive("ws-archive", archive, extractedRoot, ImportMode.DIRECT_IMPORT);

        GateFixture fixture = fixture(request);

        ImportGateDecision decision = DirectImportGate.defaults()
                .evaluate(fixture.request(), fixture.inventory(), fixture.report());

        assertFalse(decision.allowed());
        assertTrue(decision.hasBlockingIssue("UNSAFE_ARCHIVE"));
    }

    @Test
    void blocksDirectImportWhenWorkspaceHasNoAnalyzableJavaContent() throws IOException {
        write("docs/readme.md", "# notes\n");

        GateFixture fixture = fixture(ImportRequest.localFolder("ws-docs", tempDir, ImportMode.DIRECT_IMPORT));

        ImportGateDecision decision = DirectImportGate.defaults()
                .evaluate(fixture.request(), fixture.inventory(), fixture.report());

        assertFalse(decision.allowed());
        assertTrue(decision.hasBlockingIssue("NO_JAVA_CONTENT"));
    }

    @Test
    void blocksDirectImportWhenWorkspaceOnlyHasBuildMetadata() throws IOException {
        write("pom.xml", "<project />\n");

        GateFixture fixture = fixture(ImportRequest.localFolder("ws-build-only", tempDir, ImportMode.DIRECT_IMPORT));

        ImportGateDecision decision = DirectImportGate.defaults()
                .evaluate(fixture.request(), fixture.inventory(), fixture.report());

        assertFalse(decision.allowed());
        assertTrue(decision.hasBlockingIssue("NO_JAVA_CONTENT"));
    }

    @Test
    void allowsDirectImportWhenWorkspaceOnlyHasBytecodeArtifacts() throws IOException {
        write("lib/app.jar", new byte[] {1, 2, 3});

        GateFixture fixture = fixture(ImportRequest.localFolder("ws-bytecode", tempDir, ImportMode.DIRECT_IMPORT));

        ImportGateDecision decision = DirectImportGate.defaults()
                .evaluate(fixture.request(), fixture.inventory(), fixture.report());

        assertTrue(fixture.report().capabilityCoverage().contains(CapabilityArea.JAVA_BYTECODE));
        assertTrue(decision.allowed());
    }

    @Test
    void allowsDirectImportWhenBytecodeArtifactsExceedInventoryTextLimit() throws IOException {
        write("lib/app.jar", new byte[] {1, 2, 3});

        GateFixture fixture = fixture(
                ImportRequest.localFolder("ws-large-bytecode", tempDir, ImportMode.DIRECT_IMPORT),
                new WorkspaceInventoryScanner(1));

        ImportGateDecision decision = DirectImportGate.defaults()
                .evaluate(fixture.request(), fixture.inventory(), fixture.report());

        assertTrue(fixture.report().capabilityCoverage().contains(CapabilityArea.JAVA_BYTECODE));
        assertTrue(decision.allowed());
    }

    @Test
    void blocksDirectImportWhenAnyPathIsUnreadable() throws IOException {
        write("src/main/java/App.java", "class App {}\n");
        write("src/main/java/Secret.java", "class Secret {}\n");
        WorkspaceInventoryScanner scanner = new WorkspaceInventoryScanner(1024, (maxFileBytes, sourceRoot, file) -> {
            if (file.getFileName().toString().equals("Secret.java")) {
                throw new AccessDeniedException(file.toString());
            }
            return WorkspaceInventoryScanner.toEntry(maxFileBytes, sourceRoot, file);
        });

        GateFixture fixture = fixture(
                ImportRequest.localFolder("ws-unreadable", tempDir, ImportMode.DIRECT_IMPORT),
                scanner);

        ImportGateDecision decision = DirectImportGate.defaults()
                .evaluate(fixture.request(), fixture.inventory(), fixture.report());

        assertFalse(decision.allowed());
        assertTrue(decision.hasBlockingIssue("UNREADABLE_PATH"));
    }

    private GateFixture fixture(ImportRequest request) throws IOException {
        return fixture(request, WorkspaceInventoryScanner.defaults());
    }

    private GateFixture fixture(ImportRequest request, WorkspaceInventoryScanner scanner) throws IOException {
        WorkspaceInventory inventory = scanner.scan(request);
        WorkspaceLayoutProfile layoutProfile = WorkspaceLayoutDetector.defaults().detect(inventory);
        ImportReviewReport report = ImportReviewReportGenerator.defaults().generate(inventory, layoutProfile);
        return new GateFixture(request, inventory, report);
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

    private record GateFixture(
            ImportRequest request,
            WorkspaceInventory inventory,
            ImportReviewReport report) {
    }
}
