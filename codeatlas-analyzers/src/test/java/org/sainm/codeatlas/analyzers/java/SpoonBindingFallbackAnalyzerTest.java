package org.sainm.codeatlas.analyzers.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.graph.model.SymbolKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpoonBindingFallbackAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void fallsBackToNoClasspathWhenBindingModelCannotResolveLegacyDependencies() throws Exception {
        Path source = tempDir.resolve("src/main/java/com/acme/LegacyAction.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package com.acme;

            class LegacyAction extends missing.vendor.LegacyBaseAction {
                void execute(missing.vendor.LegacyRequest request) {
                    request.touch();
                }
            }
            """);
        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "src/main/java", tempDir);

        SpoonBindingFallbackResult result = new SpoonBindingFallbackAnalyzer().analyze(scope, "shop", "src/main/java", List.of(source));

        assertEquals(SpoonBindingFallbackMode.NO_CLASSPATH_FALLBACK, result.mode());
        assertTrue(result.fallbackReason().contains("binding"));
        assertTrue(result.analysis().nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.CLASS
            && node.symbolId().ownerQualifiedName().equals("com.acme.LegacyAction")));
    }
}
