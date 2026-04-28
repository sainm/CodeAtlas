package org.sainm.codeatlas.analyzers.java;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpoonVariableTraceAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsMethodLocalVariableEvents() throws Exception {
        Path source = tempDir.resolve("src/main/java/com/acme/UserService.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package com.acme;

            class UserService {
                String normalize(String name) {
                    String trimmed = name.trim();
                    trimmed = trimmed.toLowerCase();
                    return trimmed;
                }
            }
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "src/main/java", tempDir);
        VariableTraceResult result = new SpoonVariableTraceAnalyzer().analyze(scope, "shop", "src/main/java", List.of(source));

        assertTrue(result.events().stream().anyMatch(event -> event.kind() == VariableEventKind.PARAMETER
            && event.variableName().equals("name")));
        assertTrue(result.events().stream().anyMatch(event -> event.kind() == VariableEventKind.LOCAL_DEFINITION
            && event.variableName().equals("trimmed")
            && event.expression().contains("trim")));
        assertTrue(result.events().stream().anyMatch(event -> event.kind() == VariableEventKind.ASSIGNMENT
            && event.variableName().equals("trimmed")
            && event.expression().contains("toLowerCase")));
        assertTrue(result.events().stream().anyMatch(event -> event.kind() == VariableEventKind.RETURN
            && event.expression().equals("trimmed")));
    }

    @Test
    void extractsRequestParameterAndAttributeAccesses() throws Exception {
        Path source = tempDir.resolve("src/main/java/com/acme/UserAction.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package com.acme;

            class UserAction {
                void execute(javax.servlet.http.HttpServletRequest request) {
                    String id = request.getParameter("id");
                    Object user = request.getAttribute("user");
                    request.setAttribute("result", user);
                }
            }
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "src/main/java", tempDir);
        VariableTraceResult result = new SpoonVariableTraceAnalyzer().analyze(scope, "shop", "src/main/java", List.of(source));

        assertTrue(result.events().stream().anyMatch(event -> event.kind() == VariableEventKind.REQUEST_PARAMETER_READ
            && event.variableName().equals("id")));
        assertTrue(result.events().stream().anyMatch(event -> event.kind() == VariableEventKind.REQUEST_ATTRIBUTE_READ
            && event.variableName().equals("user")));
        assertTrue(result.events().stream().anyMatch(event -> event.kind() == VariableEventKind.REQUEST_ATTRIBUTE_WRITE
            && event.variableName().equals("result")));
    }

    @Test
    void extractsSimpleGetterAndSetterPropagationEvents() throws Exception {
        Path source = tempDir.resolve("src/main/java/com/acme/UserForm.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package com.acme;

            class UserForm {
                private String userId;

                String getUserId() {
                    return userId;
                }

                void setUserId(String userId) {
                    this.userId = userId;
                }
            }
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "src/main/java", tempDir);
        VariableTraceResult result = new SpoonVariableTraceAnalyzer().analyze(scope, "shop", "src/main/java", List.of(source));

        assertTrue(result.events().stream().anyMatch(event -> event.kind() == VariableEventKind.GETTER_RETURN
            && event.variableName().equals("userId")
            && event.expression().equals("userId")));
        assertTrue(result.events().stream().anyMatch(event -> event.kind() == VariableEventKind.SETTER_WRITE
            && event.variableName().equals("userId")
            && event.expression().equals("userId")));
    }
}
