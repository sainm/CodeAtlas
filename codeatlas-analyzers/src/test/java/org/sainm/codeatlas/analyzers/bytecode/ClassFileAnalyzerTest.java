package org.sainm.codeatlas.analyzers.bytecode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.RelationType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClassFileAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsMethodCallsFromBusinessJarBytecode() throws Exception {
        Path jar = compileBusinessJar();
        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "project", tempDir);

        ClassFileAnalysisResult result = new ClassFileAnalyzer().analyze(scope, "shop", "src/main/java", List.of(jar));

        String apply = "method://shop/_root/src/main/java/com.vendor.logic.PricingLogic#apply(java.lang.String):void";
        String normalize = "method://shop/_root/src/main/java/com.vendor.logic.PricingUtil#normalize(java.lang.String):java.lang.String";
        String save = "method://shop/_root/src/main/java/com.vendor.logic.PricingDao#save(java.lang.String):void";
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().value().equals(apply)));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().value().equals(normalize)));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().value().equals(save)));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.CALLS
            && fact.factKey().source().value().equals(apply)
            && fact.factKey().target().value().equals(normalize)
            && fact.factKey().qualifier().equals("bytecode-static")
            && fact.confidence() == Confidence.CERTAIN));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.CALLS
            && fact.factKey().source().value().equals(apply)
            && fact.factKey().target().value().equals(save)
            && fact.factKey().qualifier().equals("bytecode-virtual")
            && fact.confidence() == Confidence.LIKELY));
    }

    @Test
    void keepsInheritanceAndInterfaceEdgesFromClassFiles() throws Exception {
        Path jar = compileBusinessJar();
        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "project", tempDir);

        ClassFileAnalysisResult result = new ClassFileAnalyzer().analyze(scope, "shop", "src/main/java", List.of(jar));

        assertEquals(1, result.facts().stream()
            .filter(fact -> fact.factKey().relationType() == RelationType.IMPLEMENTS
                && fact.factKey().source().ownerQualifiedName().equals("com.vendor.logic.PricingLogic")
                && fact.factKey().target().ownerQualifiedName().equals("com.vendor.logic.BusinessLogic"))
            .count());
    }

    private Path compileBusinessJar() throws Exception {
        Path source = tempDir.resolve("business-jar-src/com/vendor/logic/PricingLogic.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package com.vendor.logic;
            interface BusinessLogic {
              void apply(String userId);
            }
            public class PricingLogic implements BusinessLogic {
              private final PricingDao dao = new PricingDao();
              public void apply(String userId) {
                String normalized = PricingUtil.normalize(userId);
                dao.save(normalized);
              }
            }
            class PricingDao {
              void save(String userId) {
              }
            }
            class PricingUtil {
              static String normalize(String value) {
                return value;
              }
            }
            """);
        Path classes = tempDir.resolve("business-jar-classes");
        Files.createDirectories(classes);
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertTrue(compiler != null, "JDK compiler is required for classfile fixture");
        int exitCode = compiler.run(null, null, null, "-d", classes.toString(), source.toString());
        assertEquals(0, exitCode, "business jar fixture compilation failed");

        Path jar = tempDir.resolve("src/main/webapp/WEB-INF/lib/pricing-logic.jar");
        Files.createDirectories(jar.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            addClass(output, classes, "com/vendor/logic/BusinessLogic.class");
            addClass(output, classes, "com/vendor/logic/PricingLogic.class");
            addClass(output, classes, "com/vendor/logic/PricingDao.class");
            addClass(output, classes, "com/vendor/logic/PricingUtil.class");
        }
        return jar;
    }

    private static void addClass(JarOutputStream output, Path classes, String entryName) throws Exception {
        output.putNextEntry(new JarEntry(entryName));
        Files.copy(classes.resolve(entryName), output);
        output.closeEntry();
    }
}
