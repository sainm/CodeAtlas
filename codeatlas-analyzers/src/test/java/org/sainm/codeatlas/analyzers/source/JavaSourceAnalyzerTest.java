package org.sainm.codeatlas.analyzers.source;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaSourceAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsSourceSymbolsAnnotationsLocationsAndDirectInvocations() throws IOException {
        write("src/main/java/com/acme/App.java", """
                package com.acme;

                @Deprecated
                class App {
                    private Service service;

                    @Deprecated
                    void run(String input) {
                        service.save(input);
                        helper();
                    }

                    private void helper() {}
                }
                """);
        write("src/main/java/com/acme/Service.java", """
                package com.acme;

                interface Service {
                    void save(String value);
                }
                """);

        JavaSourceAnalysisResult result = JavaSourceAnalyzer.defaults().analyze(
                tempDir,
                List.of(
                        tempDir.resolve("src/main/java/com/acme/App.java"),
                        tempDir.resolve("src/main/java/com/acme/Service.java")));

        assertFalse(result.noClasspathFallbackUsed());
        assertTrue(result.classes().stream().anyMatch(type -> type.qualifiedName().equals("com.acme.App")
                && type.annotations().contains("java.lang.Deprecated")
                && type.location().relativePath().equals("src/main/java/com/acme/App.java")));
        assertTrue(result.fields().stream().anyMatch(field -> field.ownerQualifiedName().equals("com.acme.App")
                && field.simpleName().equals("service")
                && field.typeName().equals("com.acme.Service")));
        assertTrue(result.methods().stream().anyMatch(method -> method.ownerQualifiedName().equals("com.acme.App")
                && method.simpleName().equals("run")
                && method.annotations().contains("java.lang.Deprecated")));
        assertTrue(result.directInvocations().stream().anyMatch(invocation -> invocation.ownerMethodName().equals("run")
                && invocation.targetSimpleName().equals("save")));
        assertTrue(result.directInvocations().stream().anyMatch(invocation -> invocation.ownerMethodName().equals("run")
                && invocation.targetSimpleName().equals("helper")));
    }

    @Test
    void fallsBackToNoClasspathWhenDependenciesAreMissing() throws IOException {
        write("src/main/java/com/acme/Broken.java", """
                package com.acme;

                import missing.ExternalService;

                class Broken {
                    void call(ExternalService service) {
                        service.execute();
                    }
                }
                """);

        JavaSourceAnalysisResult result = JavaSourceAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/java/com/acme/Broken.java")));

        assertTrue(result.noClasspathFallbackUsed());
        assertTrue(result.classes().stream().anyMatch(type -> type.qualifiedName().equals("com.acme.Broken")));
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("NO_CLASSPATH_FALLBACK")));
        assertTrue(result.directInvocations().stream()
                .anyMatch(invocation -> invocation.targetSimpleName().equals("execute")));
    }

    @Test
    void emitsErasedJvmDescriptorsForGenericMethodsAndFields() throws IOException {
        write("src/main/java/com/acme/Box.java", """
                package com.acme;

                class Box<T> {
                    private T value;
                    private T[] values;

                    void set(T value) {
                        this.value = value;
                    }

                    void setAll(T[] values) {
                        this.values = values;
                    }
                }
                """);

        JavaSourceAnalysisResult result = JavaSourceAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/java/com/acme/Box.java")));

        assertTrue(result.methods().stream().anyMatch(method -> method.ownerQualifiedName().equals("com.acme.Box")
                && method.simpleName().equals("set")
                && method.signature().equals("(Ljava/lang/Object;)V")));
        assertTrue(result.fields().stream().anyMatch(field -> field.ownerQualifiedName().equals("com.acme.Box")
                && field.simpleName().equals("value")
                && field.typeDescriptor().equals("Ljava/lang/Object;")));
        assertTrue(result.methods().stream().anyMatch(method -> method.ownerQualifiedName().equals("com.acme.Box")
                && method.simpleName().equals("setAll")
                && method.signature().equals("([Ljava/lang/Object;)V")));
        assertTrue(result.fields().stream().anyMatch(field -> field.ownerQualifiedName().equals("com.acme.Box")
                && field.simpleName().equals("values")
                && field.typeDescriptor().equals("[Ljava/lang/Object;")));
    }

    @Test
    void extractsConstructorsAndInvocationsInsideConstructorBodies() throws IOException {
        write("src/main/java/com/acme/App.java", """
                package com.acme;

                class App {
                    App(Service service) {
                        service.save();
                    }
                }

                class Service {
                    void save() {}
                }
                """);

        JavaSourceAnalysisResult result = JavaSourceAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/java/com/acme/App.java")));

        assertTrue(result.methods().stream().anyMatch(method -> method.ownerQualifiedName().equals("com.acme.App")
                && method.simpleName().equals("<init>")
                && method.signature().equals("(Lcom/acme/Service;)V")));
        assertTrue(result.directInvocations().stream().anyMatch(invocation -> invocation.ownerQualifiedName().equals("com.acme.App")
                && invocation.ownerMethodName().equals("<init>")
                && invocation.ownerMethodSignature().equals("(Lcom/acme/Service;)V")
                && invocation.targetQualifiedName().equals("com.acme.Service")
                && invocation.targetSimpleName().equals("save")));
    }

    @Test
    void extractsConstructorCallsFromMethodBodies() throws IOException {
        write("src/main/java/com/acme/App.java", """
                package com.acme;

                class App {
                    void run() {
                        new Service();
                    }
                }

                class Service {}
                """);

        JavaSourceAnalysisResult result = JavaSourceAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/java/com/acme/App.java")));

        assertTrue(result.directInvocations().stream().anyMatch(invocation -> invocation.ownerQualifiedName().equals("com.acme.App")
                && invocation.ownerMethodName().equals("run")
                && invocation.targetQualifiedName().equals("com.acme.Service")
                && invocation.targetSimpleName().equals("<init>")
                && invocation.targetSignature().equals("()V")));
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
