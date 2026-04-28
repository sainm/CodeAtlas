package org.sainm.codeatlas.analyzers.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SymbolKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpringMvcAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsSpringRequestMappingEntrypoint() throws Exception {
        Path source = tempDir.resolve("src/main/java/com/acme/UserController.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package com.acme;

            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            @RequestMapping("/users")
            class UserController {
                @GetMapping("/{id}")
                String find(String id) {
                    return id;
                }
            }
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "src/main/java", tempDir);
        SpringMvcAnalysisResult result = new SpringMvcAnalyzer().analyze(scope, "shop", "src/main/java", List.of(source));

        assertEquals(1, result.endpoints().size());
        assertEquals("GET", result.endpoints().getFirst().httpMethod());
        assertEquals("/users/{id}", result.endpoints().getFirst().path());
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.API_ENDPOINT));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.ROUTES_TO
            && fact.factKey().source().kind() == SymbolKind.API_ENDPOINT
            && fact.factKey().target().memberName().equals("find")));
    }
}
