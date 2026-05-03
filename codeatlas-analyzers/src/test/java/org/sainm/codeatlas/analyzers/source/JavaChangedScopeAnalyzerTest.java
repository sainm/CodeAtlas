package org.sainm.codeatlas.analyzers.source;

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
import org.sainm.codeatlas.analyzers.workspace.GitDiffSummary;
import org.sainm.codeatlas.analyzers.workspace.ImportMode;
import org.sainm.codeatlas.analyzers.workspace.ImportRequest;
import org.sainm.codeatlas.analyzers.workspace.WorkspaceInventory;
import org.sainm.codeatlas.analyzers.workspace.WorkspaceInventoryScanner;

class JavaChangedScopeAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void analyzesOnlyChangedJavaFilesAndReusesFileHashCache() throws IOException {
        write("src/main/java/com/acme/A.java", "package com.acme; class A {}\n");
        write("src/main/java/com/acme/B.java", "package com.acme; class B {}\n");
        WorkspaceInventory inventory = WorkspaceInventoryScanner.defaults()
                .scan(ImportRequest.localFolder("ws-java", tempDir, ImportMode.DIRECT_IMPORT));
        JavaSourceIncrementalCache cache = new JavaSourceIncrementalCache();

        JavaChangedScopeAnalysis first = JavaChangedScopeAnalyzer.defaults().analyze(
                tempDir,
                inventory,
                new GitDiffSummary(List.of("src/main/java/com/acme/A.java")),
                cache);
        JavaChangedScopeAnalysis second = JavaChangedScopeAnalyzer.defaults().analyze(
                tempDir,
                inventory,
                new GitDiffSummary(List.of("src/main/java/com/acme/A.java")),
                cache);

        assertEquals(List.of("src/main/java/com/acme/A.java"), first.analyzedPaths());
        assertTrue(first.result().classes().stream().anyMatch(type -> type.qualifiedName().equals("com.acme.A")));
        assertFalse(first.result().classes().stream().anyMatch(type -> type.qualifiedName().equals("com.acme.B")));
        assertEquals(0, first.cacheHits());
        assertEquals(1, second.cacheHits());
        assertEquals(List.of("src/main/java/com/acme/A.java"), cache.cachedPaths());
        assertFalse(cache.containsAstObjects());
    }

    @Test
    void skipsChangedJavaFilesThatInventoryMarkedSkipped() throws IOException {
        write("src/main/java/com/acme/Broken.java", new byte[] {(byte) 0xc3, 0x28});
        WorkspaceInventory inventory = WorkspaceInventoryScanner.defaults()
                .scan(ImportRequest.localFolder("ws-java", tempDir, ImportMode.DIRECT_IMPORT));

        JavaChangedScopeAnalysis analysis = JavaChangedScopeAnalyzer.defaults().analyze(
                tempDir,
                inventory,
                new GitDiffSummary(List.of("src/main/java/com/acme/Broken.java")),
                new JavaSourceIncrementalCache());

        assertTrue(analysis.analyzedPaths().isEmpty());
        assertTrue(analysis.result().classes().isEmpty());
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
