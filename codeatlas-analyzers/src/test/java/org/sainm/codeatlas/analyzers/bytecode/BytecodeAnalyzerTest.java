package org.sainm.codeatlas.analyzers.bytecode;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BytecodeAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void scansJarClassesMethodsFieldsAnnotationsInheritanceAndMethodCalls() throws IOException {
        write("src/com/acme/App.java", """
                package com.acme;

                @Deprecated
                class App extends Base {
                    private String name;

                    void run() {
                        helper();
                        new Service().save();
                    }

                    private void helper() {}
                }
                class Base {}
                class Service { void save() {} }
                """);
        Path classesDir = Files.createDirectories(tempDir.resolve("classes"));
        int result = ToolProvider.getSystemJavaCompiler().run(
                null,
                null,
                null,
                "--release",
                "17",
                "-d",
                classesDir.toString(),
                tempDir.resolve("src/com/acme/App.java").toString());
        if (result != 0) {
            throw new AssertionError("javac failed with exit code " + result);
        }
        Path jar = jar(classesDir);

        BytecodeAnalysisResult analysis = BytecodeAnalyzer.defaults().analyze(List.of(jar));

        assertTrue(analysis.classes().stream().anyMatch(type -> type.qualifiedName().equals("com.acme.App")
                && type.superClassName().equals("com.acme.Base")
                && type.annotations().contains("java.lang.Deprecated")));
        assertTrue(analysis.fields().stream().anyMatch(field -> field.ownerQualifiedName().equals("com.acme.App")
                && field.simpleName().equals("name")
                && field.typeName().equals("java.lang.String")));
        assertTrue(analysis.methods().stream().anyMatch(method -> method.ownerQualifiedName().equals("com.acme.App")
                && method.simpleName().equals("run")
                && method.descriptor().equals("()V")));
        assertTrue(analysis.methodCalls().stream().anyMatch(call -> call.ownerQualifiedName().equals("com.acme.App")
                && call.ownerMethodName().equals("run")
                && call.targetQualifiedName().equals("com.acme.App")
                && call.targetMethodName().equals("helper")
                && call.targetDescriptor().equals("()V")));
        assertTrue(analysis.methodCalls().stream().anyMatch(call -> call.ownerQualifiedName().equals("com.acme.App")
                && call.ownerMethodName().equals("run")
                && call.targetQualifiedName().equals("com.acme.Service")
                && call.targetMethodName().equals("save")
                && call.targetDescriptor().equals("()V")));
    }

    private Path jar(Path classesDir) throws IOException {
        Path jar = tempDir.resolve("app.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar));
                Stream<Path> paths = Files.walk(classesDir)) {
            for (Path classFile : paths.filter(Files::isRegularFile).toList()) {
                String entryName = classesDir.relativize(classFile).toString().replace('\\', '/');
                output.putNextEntry(new JarEntry(entryName));
                Files.copy(classFile, output);
                output.closeEntry();
            }
        }
        return jar;
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
