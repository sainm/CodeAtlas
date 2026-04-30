package org.sainm.codeatlas.analyzers.java;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.graph.model.NodeRole;
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

    @Test
    void extractsConstructorsInitializersNestedTypesAndLambdasWithStableIds() throws Exception {
        Path source = tempDir.resolve("src/main/java/com/acme/UserService.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package com.acme;

            class UserService {
                static {
                    Helper.boot();
                }

                {
                    Helper.warm();
                }

                UserService(String name) {
                    Helper.touch(name);
                }

                void run() {
                    Runnable task = () -> Helper.run();
                    task.run();
                }

                static class Nested {
                    void nested() {
                    }
                }
            }

            class Helper {
                static void boot() {}
                static void warm() {}
                static void touch(String value) {}
                static void run() {}
            }
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "src/main/java", tempDir);
        JavaAnalysisResult result = new SpoonJavaAnalyzer().analyze(scope, "shop", "src/main/java", List.of(source));

        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.METHOD
            && node.symbolId().memberName().equals("<init>")
            && node.symbolId().descriptor().contains("java.lang.String")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.METHOD
            && node.symbolId().memberName().equals("<clinit>")
            && node.symbolId().descriptor().contains("@")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.METHOD
            && node.symbolId().memberName().equals("<init-block>")
            && node.symbolId().descriptor().contains("@")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.METHOD
            && node.symbolId().memberName().startsWith("lambda$")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.CLASS
            && node.symbolId().ownerQualifiedName().contains("UserService")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.CALLS
            && fact.factKey().source().memberName().equals("<clinit>")
            && fact.factKey().target().memberName().equals("boot")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.CALLS
            && fact.factKey().source().memberName().startsWith("lambda$")
            && fact.factKey().target().memberName().equals("run")));
    }

    @Test
    void marksStatelessStaticSupportClassesAndStaticUtilityCalls() throws Exception {
        Path source = tempDir.resolve("src/main/java/com/acme/UserAction.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package com.acme;

            import com.acme.common.LegacyStringUtil;

            class UserAction {
                void execute(String name) {
                    LegacyStringUtil.normalize(name);
                }
            }
            """);
        Path utility = tempDir.resolve("src/main/java/com/acme/common/LegacyStringUtil.java");
        Files.createDirectories(utility.getParent());
        Files.writeString(utility, """
            package com.acme.common;

            public final class LegacyStringUtil {
                public static String normalize(String value) {
                    return value == null ? "" : value.trim();
                }
            }
            """);
        Path statefulSupport = tempDir.resolve("src/main/java/com/acme/common/BaseSupport.java");
        Files.writeString(statefulSupport, """
            package com.acme.common;

            public class BaseSupport {
                private String state;

                public String state() {
                    return state;
                }
            }
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "src/main/java", tempDir);
        JavaAnalysisResult result = new SpoonJavaAnalyzer().analyze(scope, "shop", "src/main/java", List.of(source, utility, statefulSupport));

        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.CLASS
            && node.symbolId().ownerQualifiedName().equals("com.acme.common.LegacyStringUtil")
            && node.roles().contains(NodeRole.UTILITY)));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.METHOD
            && node.symbolId().ownerQualifiedName().equals("com.acme.common.LegacyStringUtil")
            && node.symbolId().memberName().equals("normalize")
            && node.roles().contains(NodeRole.UTILITY)));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.CALLS
            && fact.factKey().source().ownerQualifiedName().equals("com.acme.UserAction")
            && fact.factKey().target().ownerQualifiedName().equals("com.acme.common.LegacyStringUtil")
            && fact.factKey().target().memberName().equals("normalize")
            && fact.factKey().qualifier().equals("static-utility")));
        assertFalse(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.CLASS
            && node.symbolId().ownerQualifiedName().equals("com.acme.common.BaseSupport")
            && node.roles().contains(NodeRole.UTILITY)));
    }

    @Test
    void marksIndirectStrutsActionSubclasses() throws Exception {
        Path source = tempDir.resolve("src/main/java/com/acme/UserAction.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package com.acme;

            abstract class BaseAction extends org.apache.struts.action.Action {
            }

            class UserAction extends BaseAction {
            }
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "src/main/java", tempDir);
        JavaAnalysisResult result = new SpoonJavaAnalyzer().analyze(scope, "shop", "src/main/java", List.of(source));

        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.CLASS
            && node.symbolId().ownerQualifiedName().equals("com.acme.UserAction")
            && node.roles().contains(NodeRole.STRUTS_ACTION)));
    }
}
