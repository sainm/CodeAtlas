package org.sainm.codeatlas.analyzers.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VariableTraceAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsMethodLocalDefUseAndPropagatesSimpleAliasesInSourceOrder() throws IOException {
        write("src/main/java/com/acme/UserController.java", """
                package com.acme;

                class UserController {
                    void handle(String requestId) {
                        String id = requestId;
                        String alias = id;
                        String display = alias;
                        save(display);
                    }

                    void save(String value) {}
                }
                """);

        VariableTraceAnalysisResult result = VariableTraceAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/java/com/acme/UserController.java")));

        assertTrue(result.diagnostics().isEmpty());
        assertEquals(3, result.defUses().stream()
                .filter(defUse -> defUse.ownerQualifiedName().equals("com.acme.UserController")
                        && defUse.methodName().equals("handle"))
                .count());
        assertTrue(result.defUses().stream().anyMatch(defUse -> defUse.sourceVariableName().equals("requestId")
                && defUse.targetVariableName().equals("id")
                && defUse.resolvedSourceVariableName().equals("requestId")));
        assertTrue(result.defUses().stream().anyMatch(defUse -> defUse.sourceVariableName().equals("alias")
                && defUse.targetVariableName().equals("display")
                && defUse.resolvedSourceVariableName().equals("requestId")));
        assertTrue(result.uses().stream().anyMatch(use -> use.variableName().equals("display")
                && use.resolvedSourceVariableName().equals("requestId")
                && use.usageKind().equals("argument")));
    }

    @Test
    void tracksRequestParameterAttributeReadsAndWrites() throws IOException {
        write("src/main/java/com/acme/UserController.java", """
                package com.acme;

                import jakarta.servlet.http.HttpServletRequest;

                class UserController {
                    void handle(HttpServletRequest request) {
                        String id = request.getParameter("id");
                        String alias = id;
                        Object user = request.getAttribute("user");
                        request.setAttribute("savedId", alias);
                    }
                }
                """);

        VariableTraceAnalysisResult result = VariableTraceAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/java/com/acme/UserController.java")));

        assertTrue(result.diagnostics().isEmpty());
        assertTrue(result.requestAccesses().stream().anyMatch(access -> access.accessKind().equals("getParameter")
                && access.name().equals("id")));
        assertTrue(result.requestAccesses().stream().anyMatch(access -> access.accessKind().equals("getAttribute")
                && access.name().equals("user")));
        assertTrue(result.requestAccesses().stream().anyMatch(access -> access.accessKind().equals("setAttribute")
                && access.name().equals("savedId")
                && access.valueVariableName().equals("alias")
                && access.resolvedSourceVariableName().equals("id")));
    }

    @Test
    void tracksActionFormAndDynaActionFormPropertyReads() throws IOException {
        write("src/main/java/com/acme/UserAction.java", """
                package com.acme;

                import org.apache.struts.action.DynaActionForm;

                class UserAction {
                    void execute(UserActionForm form, DynaActionForm dyna) {
                        String name = form.getName();
                        String id = (String) dyna.get("id");
                    }
                }

                class UserActionForm {
                    String getName() { return ""; }
                }
                """);

        VariableTraceAnalysisResult result = VariableTraceAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/java/com/acme/UserAction.java")));

        assertTrue(result.diagnostics().isEmpty());
        assertTrue(result.formPropertyReads().stream().anyMatch(read -> read.formVariableName().equals("form")
                && read.propertyName().equals("name")));
        assertTrue(result.formPropertyReads().stream().anyMatch(read -> read.formVariableName().equals("dyna")
                && read.propertyName().equals("id")));
    }

    @Test
    void tracksSimpleGetterSetterPropagation() throws IOException {
        write("src/main/java/com/acme/UserMapper.java", """
                package com.acme;

                class UserMapper {
                    void copy(UserEntity entity, UserDto dto) {
                        String name = entity.getName();
                        dto.setName(name);
                    }
                }

                class UserEntity {
                    String getName() { return ""; }
                }

                class UserDto {
                    void setName(String value) {}
                }
                """);

        VariableTraceAnalysisResult result = VariableTraceAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/java/com/acme/UserMapper.java")));

        assertTrue(result.diagnostics().isEmpty());
        assertTrue(result.beanPropertyFlows().stream().anyMatch(flow -> flow.sourceObjectName().equals("entity")
                && flow.sourcePropertyName().equals("name")
                && flow.targetObjectName().equals("dto")
                && flow.targetPropertyName().equals("name")
                && flow.valueVariableName().equals("name")));
    }

    @Test
    void tracksRequestDerivedArgumentsPassedToOtherMethods() throws IOException {
        write("src/main/java/com/acme/UserController.java", """
                package com.acme;

                import jakarta.servlet.http.HttpServletRequest;

                class UserController {
                    void handle(HttpServletRequest request, UserService service) {
                        String id = request.getParameter("id");
                        String alias = id;
                        service.find(alias);
                    }
                }

                class UserService {
                    void find(String id) {}
                }
                """);

        VariableTraceAnalysisResult result = VariableTraceAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/java/com/acme/UserController.java")));

        assertTrue(result.diagnostics().isEmpty());
        assertTrue(result.requestDerivedArguments().stream().anyMatch(argument -> argument.requestParameterName().equals("id")
                && argument.variableName().equals("alias")
                && argument.targetQualifiedName().equals("com.acme.UserService")
                && argument.targetMethodName().equals("find")
                && argument.argumentIndex() == 0));
    }

    @Test
    void tracksControllerServiceDaoParameterFlow() throws IOException {
        write("src/main/java/com/acme/UserService.java", """
                package com.acme;

                class UserService {
                    void load(String id, UserDao dao) {
                        String alias = id;
                        dao.find(alias);
                    }
                }

                class UserDao {
                    void find(String id) {}
                }
                """);

        VariableTraceAnalysisResult result = VariableTraceAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/java/com/acme/UserService.java")));

        assertTrue(result.diagnostics().isEmpty());
        assertTrue(result.parameterDerivedArguments().stream().anyMatch(argument -> argument.ownerQualifiedName().equals("com.acme.UserService")
                && argument.sourceParameterIndex() == 0
                && argument.targetQualifiedName().equals("com.acme.UserDao")
                && argument.targetMethodName().equals("find")
                && argument.targetArgumentIndex() == 0));
    }

    @Test
    void coversAliasChainsAssignmentOrderStringTransformationsAndActionFormSources() throws IOException {
        write("src/main/java/com/acme/UserAction.java", """
                package com.acme;

                class UserAction {
                    void execute(UserActionForm form, UserService service) {
                        String name = form.getName();
                        String alias = name;
                        String upper = alias.trim();
                        service.save(upper);
                    }
                }

                class UserActionForm {
                    String getName() { return ""; }
                }

                class UserService {
                    void save(String value) {}
                }
                """);

        VariableTraceAnalysisResult result = VariableTraceAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/java/com/acme/UserAction.java")));

        assertTrue(result.diagnostics().isEmpty());
        assertTrue(result.formPropertyReads().stream().anyMatch(read -> read.formVariableName().equals("form")
                && read.propertyName().equals("name")));
        assertTrue(result.defUses().stream().anyMatch(defUse -> defUse.sourceVariableName().equals("name")
                && defUse.targetVariableName().equals("alias")));
        assertTrue(result.defUses().stream().anyMatch(defUse -> defUse.sourceVariableName().equals("alias")
                && defUse.targetVariableName().equals("upper")));
        assertTrue(result.parameterDerivedArguments().isEmpty());
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
