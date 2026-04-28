package org.sainm.codeatlas.analyzers.java;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SymbolKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpoonJavaAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsTypesMembersInheritanceAndDirectCalls() throws Exception {
        Path source = tempDir.resolve("src/main/java/com/acme/UserService.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package com.acme;

            @interface Marker {
            }

            interface Named {
                String name();
            }

            enum Status {
                ACTIVE
            }

            class BaseService {
            }

            class UserRepository {
                void save(String name) {}
            }

            class UserService extends BaseService implements Named {
                private final UserRepository repository = new UserRepository();

                public String name() {
                    return "user";
                }

                public void save(String name) {
                    repository.save(name);
                }
            }
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "src/main/java", tempDir);
        JavaAnalysisResult result = new SpoonJavaAnalyzer().analyze(scope, "shop", "src/main/java", List.of(source));

        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.CLASS
            && node.symbolId().ownerQualifiedName().equals("com.acme.UserService")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.INTERFACE
            && node.symbolId().ownerQualifiedName().equals("com.acme.Named")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.ENUM
            && node.symbolId().ownerQualifiedName().equals("com.acme.Status")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.ANNOTATION
            && node.symbolId().ownerQualifiedName().equals("com.acme.Marker")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.FIELD
            && node.symbolId().memberName().equals("repository")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.EXTENDS));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.IMPLEMENTS));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.CALLS
            && fact.factKey().source().memberName().equals("save")
            && fact.factKey().target().memberName().equals("save")));
    }

    @Test
    void extractsInterfaceMethodImplementationCandidates() throws Exception {
        Path source = tempDir.resolve("src/main/java/com/acme/UserRepository.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package com.acme;

            interface UserRepository {
                User find(String id);
            }

            class JdbcUserRepository implements UserRepository {
                public User find(String id) {
                    return null;
                }
            }

            class User {
            }
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "src/main/java", tempDir);
        JavaAnalysisResult result = new SpoonJavaAnalyzer().analyze(scope, "shop", "src/main/java", List.of(source));

        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.IMPLEMENTS
            && fact.factKey().source().kind() == SymbolKind.METHOD
            && fact.factKey().source().ownerQualifiedName().equals("com.acme.JdbcUserRepository")
            && fact.factKey().source().memberName().equals("find")
            && fact.factKey().target().ownerQualifiedName().equals("com.acme.UserRepository")
            && fact.factKey().target().memberName().equals("find")));
    }
}
