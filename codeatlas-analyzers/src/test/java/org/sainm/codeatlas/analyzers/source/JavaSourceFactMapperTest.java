package org.sainm.codeatlas.analyzers.source;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sainm.codeatlas.facts.Confidence;
import org.sainm.codeatlas.facts.FactRecord;

class JavaSourceFactMapperTest {
    @TempDir
    Path tempDir;

    @Test
    void mapsSourceAnalysisToDeclareAndDirectCallFacts() throws IOException {
        write("src/main/java/com/acme/App.java", """
                package com.acme;

                class App {
                    private Service service;

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

        JavaSourceFactBatch batch = JavaSourceFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/java",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/java"));

        assertFalse(batch.evidence().isEmpty());
        assertFact(batch, "DECLARES", "source-file://shop/_root/src/main/java/com/acme/App.java",
                "class://shop/_root/src/main/java/com.acme.App");
        assertFact(batch, "DECLARES", "class://shop/_root/src/main/java/com.acme.App",
                "method://shop/_root/src/main/java/com.acme.App#run(Ljava/lang/String;)V");
        assertFact(batch, "DECLARES", "class://shop/_root/src/main/java/com.acme.App",
                "field://shop/_root/src/main/java/com.acme.App#service:Lcom/acme/Service;");
        assertFact(batch, "CALLS", "method://shop/_root/src/main/java/com.acme.App#run(Ljava/lang/String;)V",
                "method://shop/_root/src/main/java/com.acme.Service#save(Ljava/lang/String;)V");
        assertFact(batch, "CALLS", "method://shop/_root/src/main/java/com.acme.App#run(Ljava/lang/String;)V",
                "method://shop/_root/src/main/java/com.acme.App#helper()V");
    }

    @Test
    void mapsCallsFromTheCorrectOverloadedOwnerMethod() throws IOException {
        write("src/main/java/com/acme/App.java", """
                package com.acme;

                class App {
                    void run(String input) {
                        target(input);
                    }

                    void run(int input) {
                        target(input);
                    }

                    void target(String input) {}
                    void target(int input) {}
                }
                """);
        JavaSourceAnalysisResult result = JavaSourceAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/java/com/acme/App.java")));

        JavaSourceFactBatch batch = JavaSourceFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/java",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/java"));

        assertFact(batch, "CALLS", "method://shop/_root/src/main/java/com.acme.App#run(Ljava/lang/String;)V",
                "method://shop/_root/src/main/java/com.acme.App#target(Ljava/lang/String;)V");
        assertFact(batch, "CALLS", "method://shop/_root/src/main/java/com.acme.App#run(I)V",
                "method://shop/_root/src/main/java/com.acme.App#target(I)V");
    }

    @Test
    void mapsRepeatedCallsToOneFactPerFactKey() throws IOException {
        write("src/main/java/com/acme/App.java", """
                package com.acme;

                class App {
                    void run() {
                        helper();
                        helper();
                    }

                    private void helper() {}
                }
                """);
        JavaSourceAnalysisResult result = JavaSourceAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/java/com/acme/App.java")));

        JavaSourceFactBatch batch = JavaSourceFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/java",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/java"));

        long repeatedCallFacts = batch.facts().stream()
                .filter(fact -> matches(
                        fact,
                        "CALLS",
                        "method://shop/_root/src/main/java/com.acme.App#run()V",
                        "method://shop/_root/src/main/java/com.acme.App#helper()V"))
                .count();
        assertEquals(1, repeatedCallFacts);
    }

    @Test
    void mapsConstructorCallsToInitFacts() throws IOException {
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

        JavaSourceFactBatch batch = JavaSourceFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/java",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/java"));

        assertFact(batch, "CALLS", "method://shop/_root/src/main/java/com.acme.App#run()V",
                "method://shop/_root/src/main/java/com.acme.Service#<init>()V");
    }

    @Test
    void downgradesFactsWhenSourceAnalysisUsedNoClasspathFallback() throws IOException {
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

        JavaSourceFactBatch batch = JavaSourceFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/java",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/java"));

        assertFalse(batch.facts().isEmpty());
        assertTrue(batch.facts().stream().allMatch(fact -> fact.confidence() == Confidence.LIKELY));
    }

    private static void assertFact(
            JavaSourceFactBatch batch,
            String relationName,
            String sourceIdentityId,
            String targetIdentityId) {
        assertTrue(batch.facts().stream().anyMatch(fact -> matches(fact, relationName, sourceIdentityId, targetIdentityId)),
                () -> "Missing " + relationName + " fact from " + sourceIdentityId + " to " + targetIdentityId
                        + " in " + batch.facts());
    }

    private static boolean matches(
            FactRecord fact,
            String relationName,
            String sourceIdentityId,
            String targetIdentityId) {
        return fact.relationType().name().equals(relationName)
                && fact.sourceIdentityId().equals(sourceIdentityId)
                && fact.targetIdentityId().equals(targetIdentityId);
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
