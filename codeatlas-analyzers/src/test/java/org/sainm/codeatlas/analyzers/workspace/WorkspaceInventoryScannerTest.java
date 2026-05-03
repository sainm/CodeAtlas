package org.sainm.codeatlas.analyzers.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceInventoryScannerTest {
    @TempDir
    Path tempDir;

    @Test
    void scansLocalFolderInventoryWithHashesAndCapabilityLevels() throws IOException {
        write("src/main/java/com/acme/App.java", "package com.acme; class App {}\n");
        write("pom.xml", "<project />\n");
        write("native/module.c", "int main(void) { return 0; }\n");
        write("docs/readme.md", "# notes\n");
        write("bin/app.exe", new byte[] {0, 1, 2, 3});

        WorkspaceInventory inventory = WorkspaceInventoryScanner.defaults()
                .scan(ImportRequest.localFolder("ws-1", tempDir, ImportMode.ASSISTED_IMPORT_REVIEW));

        assertEquals(ImportSourceType.LOCAL_FOLDER, inventory.sourceType());
        assertEquals(ImportMode.ASSISTED_IMPORT_REVIEW, inventory.mode());
        assertEquals(5, inventory.entries().size());
        assertEquals(FileCapabilityLevel.L1_STRUCTURED, inventory.requireEntry("src/main/java/com/acme/App.java").level());
        assertEquals(FileCapabilityLevel.L2_SEMI_STRUCTURED, inventory.requireEntry("pom.xml").level());
        assertEquals(FileCapabilityLevel.L3_BOUNDARY, inventory.requireEntry("native/module.c").level());
        assertEquals(FileCapabilityLevel.L4_INVENTORY, inventory.requireEntry("docs/readme.md").level());
        assertEquals(FileCapabilityLevel.L3_BOUNDARY, inventory.requireEntry("bin/app.exe").level());
        assertEquals(64, inventory.requireEntry("pom.xml").sha256().length());
        assertFalse(inventory.requireEntry("pom.xml").decodeDiagnostic().binary());
        assertTrue(inventory.requireEntry("bin/app.exe").decodeDiagnostic().binary());
    }

    @Test
    void supportsUploadedArchiveAsAnExtractedWorkspaceSource() throws IOException {
        Path archive = tempDir.resolve("upload.zip");
        Files.write(archive, new byte[] {1, 2, 3});
        Path extractedRoot = Files.createDirectory(tempDir.resolve("extracted"));
        Files.writeString(extractedRoot.resolve("build.gradle"), "plugins {}\n", StandardCharsets.UTF_8);

        WorkspaceInventory inventory = WorkspaceInventoryScanner.defaults()
                .scan(ImportRequest.uploadedArchive("ws-2", archive, extractedRoot, ImportMode.DIRECT_IMPORT));

        assertEquals(ImportSourceType.UPLOADED_ARCHIVE, inventory.sourceType());
        assertEquals(archive, inventory.archivePath().orElseThrow());
        assertEquals(FileCapabilityLevel.L2_SEMI_STRUCTURED, inventory.requireEntry("build.gradle").level());
    }

    @Test
    void marksOversizedFilesAsSkippedWithDiagnostic() throws IOException {
        write("huge.sql", "select * from users;\n");

        WorkspaceInventory inventory = new WorkspaceInventoryScanner(8)
                .scan(ImportRequest.localFolder("ws-3", tempDir, ImportMode.DIRECT_IMPORT));

        FileInventoryEntry entry = inventory.requireEntry("huge.sql");

        assertEquals(FileCapabilityLevel.L5_SKIPPED, entry.level());
        assertEquals("FILE_TOO_LARGE", entry.decodeDiagnostic().code());
    }

    @Test
    void doesNotHashOversizedFilesBeforeSkippingThem() throws IOException {
        write("huge.sql", "select * from users;\n");

        WorkspaceInventory inventory = new WorkspaceInventoryScanner(8)
                .scan(ImportRequest.localFolder("ws-oversized", tempDir, ImportMode.DIRECT_IMPORT));

        FileInventoryEntry entry = inventory.requireEntry("huge.sql");

        assertEquals(FileCapabilityLevel.L5_SKIPPED, entry.level());
        assertEquals("", entry.sha256());
        assertEquals("FILE_TOO_LARGE", entry.decodeDiagnostic().code());
    }

    @Test
    void keepsOversizedBytecodeArtifactsAnalyzable() throws IOException {
        write("lib/app.jar", new byte[] {1, 2, 3});

        WorkspaceInventory inventory = new WorkspaceInventoryScanner(1)
                .scan(ImportRequest.localFolder("ws-large-bytecode", tempDir, ImportMode.DIRECT_IMPORT));

        FileInventoryEntry entry = inventory.requireEntry("lib/app.jar");

        assertEquals(FileCapabilityLevel.L1_STRUCTURED, entry.level());
        assertEquals(64, entry.sha256().length());
        assertTrue(entry.decodeDiagnostic().binary());
    }

    @Test
    void marksDecodeFailuresAsSkippedWithDiagnostic() throws IOException {
        write("src/main/java/Broken.java", new byte[] {(byte) 0xc3, 0x28});

        WorkspaceInventory inventory = WorkspaceInventoryScanner.defaults()
                .scan(ImportRequest.localFolder("ws-4", tempDir, ImportMode.DIRECT_IMPORT));

        FileInventoryEntry entry = inventory.requireEntry("src/main/java/Broken.java");

        assertEquals(FileCapabilityLevel.L5_SKIPPED, entry.level());
        assertEquals("DECODE_FAILED", entry.decodeDiagnostic().code());
    }

    @Test
    void allowsConsecutiveDotsInsideFileNameSegments() throws IOException {
        write("src/main/java/Foo..java", "class Foo {}\n");

        WorkspaceInventory inventory = WorkspaceInventoryScanner.defaults()
                .scan(ImportRequest.localFolder("ws-dots", tempDir, ImportMode.DIRECT_IMPORT));

        assertEquals(FileCapabilityLevel.L1_STRUCTURED, inventory.requireEntry("src/main/java/Foo..java").level());
    }

    @Test
    void rejectsUnreadableOrMissingRoots() {
        assertThrows(IllegalArgumentException.class,
                () -> ImportRequest.localFolder("ws-5", tempDir.resolve("missing"), ImportMode.DIRECT_IMPORT));
    }

    @Test
    void recordsUnreadableFilesAsSkippedDiagnostics() throws IOException {
        write("src/main/java/App.java", "class App {}\n");

        WorkspaceInventoryScanner scanner = new WorkspaceInventoryScanner(1024, (maxFileBytes, sourceRoot, file) -> {
            if (file.getFileName().toString().equals("App.java")) {
                throw new AccessDeniedException(file.toString());
            }
            return WorkspaceInventoryScanner.toEntry(maxFileBytes, sourceRoot, file);
        });
        WorkspaceInventory inventory = scanner.scan(ImportRequest.localFolder("ws-unreadable", tempDir, ImportMode.DIRECT_IMPORT));

        FileInventoryEntry entry = inventory.requireEntry("src/main/java/App.java");

        assertEquals(FileCapabilityLevel.L5_SKIPPED, entry.level());
        assertEquals("UNREADABLE_PATH", entry.decodeDiagnostic().code());
        assertEquals("", entry.sha256());
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
