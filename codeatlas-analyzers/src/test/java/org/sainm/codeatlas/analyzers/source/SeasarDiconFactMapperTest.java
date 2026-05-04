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
import org.sainm.codeatlas.facts.Confidence;
import org.sainm.codeatlas.facts.FactRecord;

class SeasarDiconFactMapperTest {
    @TempDir
    Path tempDir;

    @Test
    void mapsDiconDiscoveryToPossibleCandidateFacts() throws IOException {
        write("WEB-INF/app.dicon", """
                <components namespace="app">
                  <include path="dao.dicon"/>
                  <component name="userService" class="com.acme.service.UserService" autoBinding="auto">
                    <property name="userDao">userDao</property>
                    <aspect pointcut="get.*">traceInterceptor</aspect>
                  </component>
                  <component name="traceInterceptor" class="com.acme.TraceInterceptor"/>
                </components>
                """);
        SeasarDiconAnalysisResult result = SeasarDiconAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("WEB-INF/app.dicon")));

        JavaSourceFactBatch batch = SeasarDiconFactMapper.defaults().map(
                result,
                new SeasarDiconFactContext(
                        "shop",
                        "_root",
                        "WEB-INF",
                        "src/main/java",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "WEB-INF/app.dicon"));

        assertFalse(batch.evidence().isEmpty());
        assertTrue(batch.facts().stream().allMatch(fact -> fact.confidence() == Confidence.POSSIBLE));
        assertFact(batch,
                "DECLARES",
                "source-file://shop/_root/WEB-INF/app.dicon",
                "dicon-component://shop/_root/WEB-INF/app.dicon#component[userService]");
        assertFact(batch,
                "INCLUDES",
                "source-file://shop/_root/WEB-INF/app.dicon",
                "source-file://shop/_root/WEB-INF/dao.dicon");
        assertFact(batch,
                "AUTO_BINDS_TO",
                "dicon-component://shop/_root/WEB-INF/app.dicon#component[userService]",
                "class://shop/_root/src/main/java/com.acme.service.UserService");
        assertFact(batch,
                "CONFIGURES_PROPERTY",
                "dicon-component://shop/_root/WEB-INF/app.dicon#component[userService]",
                "config-key://shop/_root/WEB-INF/app.dicon#property[userService:userDao]");
        assertFact(batch,
                "INTERCEPTS",
                "dicon-component://shop/_root/WEB-INF/app.dicon#component[userService]",
                "dicon-component://shop/_root/WEB-INF/app.dicon#component[traceInterceptor]");
    }

    @Test
    void givesUnnamedClassComponentsDistinctIdentities() throws IOException {
        write("WEB-INF/app.dicon", """
                <components>
                  <component class="com.acme.FirstService">
                    <property name="endpoint">"local"</property>
                  </component>
                  <component class="com.acme.SecondService"/>
                </components>
                """);
        SeasarDiconAnalysisResult result = SeasarDiconAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("WEB-INF/app.dicon")));

        JavaSourceFactBatch batch = SeasarDiconFactMapper.defaults().map(
                result,
                new SeasarDiconFactContext(
                        "shop",
                        "_root",
                        "WEB-INF",
                        "src/main/java",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "WEB-INF/app.dicon"));

        assertFact(batch,
                "DECLARES",
                "source-file://shop/_root/WEB-INF/app.dicon",
                "dicon-component://shop/_root/WEB-INF/app.dicon#component[com.acme.FirstService]");
        assertFact(batch,
                "DECLARES",
                "source-file://shop/_root/WEB-INF/app.dicon",
                "dicon-component://shop/_root/WEB-INF/app.dicon#component[com.acme.SecondService]");
        assertFact(batch,
                "CONFIGURES_PROPERTY",
                "dicon-component://shop/_root/WEB-INF/app.dicon#component[com.acme.FirstService]",
                "config-key://shop/_root/WEB-INF/app.dicon#property[com.acme.FirstService:endpoint]");
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
