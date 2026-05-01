package org.sainm.codeatlas.analyzers.java;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaParserFastScannerTest {
    @TempDir
    Path tempDir;

    @Test
    void scansJavaDeclarationsWithoutBecomingPrimaryFactSource() throws Exception {
        Path source = tempDir.resolve("src/main/java/com/acme/UserAction.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package com.acme;

            interface Named {
                String name();
            }

            class BaseAction {
            }

            class UserAction extends BaseAction implements Named {
                private String id;

                UserAction(String id) {
                    this.id = id;
                }

                public String name() {
                    return id;
                }
            }
            """);
        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "src/main/java", tempDir);

        JavaAnalysisResult result = new JavaParserFastScanner().analyze(scope, "shop", "src/main/java", List.of(source));

        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.CLASS
            && node.symbolId().ownerQualifiedName().equals("com.acme.UserAction")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.INTERFACE
            && node.symbolId().ownerQualifiedName().equals("com.acme.Named")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.FIELD
            && node.symbolId().ownerQualifiedName().equals("com.acme.UserAction")
            && node.symbolId().memberName().equals("id")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.METHOD
            && node.symbolId().ownerQualifiedName().equals("com.acme.UserAction")
            && node.symbolId().memberName().equals("name")
            && node.properties().get("codeOrigin").equals("javaparser-fast")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.METHOD
            && node.symbolId().ownerQualifiedName().equals("com.acme.UserAction")
            && node.symbolId().memberName().equals("<init>")
            && node.symbolId().descriptor().equals("(java.lang.String):void")));
        assertTrue(result.facts().stream().allMatch(fact -> fact.sourceType() == SourceType.JAVAPARSER_FAST));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.EXTENDS
            && fact.factKey().source().ownerQualifiedName().equals("com.acme.UserAction")
            && fact.factKey().target().ownerQualifiedName().equals("com.acme.BaseAction")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.IMPLEMENTS
            && fact.factKey().source().ownerQualifiedName().equals("com.acme.UserAction")
            && fact.factKey().target().ownerQualifiedName().equals("com.acme.Named")));
    }
}
