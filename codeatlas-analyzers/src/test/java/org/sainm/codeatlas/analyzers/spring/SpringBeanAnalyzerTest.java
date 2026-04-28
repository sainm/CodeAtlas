package org.sainm.codeatlas.analyzers.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.NodeRole;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpringBeanAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsFieldInjectionBetweenSpringBeans() throws Exception {
        Path source = tempDir.resolve("src/main/java/com/acme/UserService.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package com.acme;

            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Repository;
            import org.springframework.stereotype.Service;

            @Service
            class UserService {
                @Autowired
                UserRepository repository;
            }

            @Repository
            class UserRepository {
            }
            """);

        SpringBeanAnalysisResult result = analyze(source);

        assertEquals(1, result.dependencies().size());
        assertEquals("com.acme.UserService", result.dependencies().getFirst().sourceClass());
        assertEquals("com.acme.UserRepository", result.dependencies().getFirst().dependencyType());
        assertEquals("field:repository", result.dependencies().getFirst().injectionPoint());
        assertEquals(Confidence.CERTAIN, result.dependencies().getFirst().confidence());
        assertTrue(result.nodes().stream().anyMatch(node -> node.roles().contains(NodeRole.SERVICE)));
        assertTrue(result.nodes().stream().anyMatch(node -> node.roles().contains(NodeRole.DAO)));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.INJECTS
            && fact.factKey().source().ownerQualifiedName().equals("com.acme.UserService")
            && fact.factKey().target().ownerQualifiedName().equals("com.acme.UserRepository")
            && fact.sourceType() == SourceType.SPOON
            && fact.confidence() == Confidence.CERTAIN));
    }

    @Test
    void extractsQualifiedConstructorInjection() throws Exception {
        Path source = tempDir.resolve("src/main/java/com/acme/UserFacade.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package com.acme;

            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.beans.factory.annotation.Qualifier;
            import org.springframework.stereotype.Repository;
            import org.springframework.stereotype.Service;

            @Service
            class UserFacade {
                @Autowired
                UserFacade(@Qualifier("mainRepo") UserRepository repository) {
                }
            }

            @Repository
            class UserRepository {
            }
            """);

        SpringBeanAnalysisResult result = analyze(source);

        assertEquals(1, result.dependencies().size());
        assertEquals("mainRepo", result.dependencies().getFirst().qualifier());
        assertEquals("constructor:repository", result.dependencies().getFirst().injectionPoint());
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.INJECTS
            && fact.factKey().qualifier().equals("constructor:repository|mainRepo")
            && fact.evidenceKey().localPath().equals("constructor:repository:mainRepo")));
    }

    private SpringBeanAnalysisResult analyze(Path source) {
        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "src/main/java", tempDir);
        return new SpringBeanAnalyzer().analyze(scope, "shop", "src/main/java", List.of(source));
    }
}
