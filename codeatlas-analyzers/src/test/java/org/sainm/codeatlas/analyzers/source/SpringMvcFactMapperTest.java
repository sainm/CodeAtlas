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

class SpringMvcFactMapperTest {
    @TempDir
    Path tempDir;

    @Test
    void mapsSpringRoutesToApiEndpointFacts() throws IOException {
        write("src/main/java/com/acme/web/OrderController.java", """
                package com.acme.web;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequestMapping("/api/orders")
                class OrderController {
                    @GetMapping("/{id}")
                    String show(String id) { return id; }
                }
                """);
        SpringMvcAnalysisResult result = SpringMvcAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/java/com/acme/web/OrderController.java")));

        JavaSourceFactBatch batch = SpringMvcFactMapper.defaults().map(
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
        assertFact(batch,
                "ROUTES_TO",
                "api-endpoint://shop/_root/_api/GET:/api/orders/{id}",
                "method://shop/_root/src/main/java/com.acme.web.OrderController#show(Ljava/lang/String;)Ljava/lang/String;");
        assertFact(batch,
                "DECLARES_ENTRYPOINT",
                "api-endpoint://shop/_root/_api/GET:/api/orders/{id}",
                "entrypoint://shop/_root/_entrypoints/spring/GET/api/orders/{id}");
        assertTrue(batch.facts().stream().allMatch(fact -> fact.confidence() == Confidence.LIKELY));
    }

    @Test
    void mapsRootSpringRouteToApiEndpointFact() throws IOException {
        write("src/main/java/com/acme/web/HomeController.java", """
                package com.acme.web;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                class HomeController {
                    @GetMapping("/")
                    String home() { return "ok"; }
                }
                """);
        SpringMvcAnalysisResult result = SpringMvcAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/java/com/acme/web/HomeController.java")));

        JavaSourceFactBatch batch = SpringMvcFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/java",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/java"));

        assertFact(batch,
                "ROUTES_TO",
                "api-endpoint://shop/_root/_api/GET:/",
                "method://shop/_root/src/main/java/com.acme.web.HomeController#home()Ljava/lang/String;");
    }

    @Test
    void mapsMethodlessRequestMappingToConcreteHttpEndpointFacts() throws IOException {
        write("src/main/java/com/acme/web/HealthController.java", """
                package com.acme.web;

                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                class HealthController {
                    @RequestMapping("/health")
                    String health() { return "ok"; }
                }
                """);
        SpringMvcAnalysisResult result = SpringMvcAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/java/com/acme/web/HealthController.java")));

        JavaSourceFactBatch batch = SpringMvcFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/java",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/java"));

        assertFact(batch,
                "ROUTES_TO",
                "api-endpoint://shop/_root/_api/GET:/health",
                "method://shop/_root/src/main/java/com.acme.web.HealthController#health()Ljava/lang/String;");
        assertFact(batch,
                "ROUTES_TO",
                "api-endpoint://shop/_root/_api/POST:/health",
                "method://shop/_root/src/main/java/com.acme.web.HealthController#health()Ljava/lang/String;");
        assertFalse(batch.facts().stream().anyMatch(fact -> fact.sourceIdentityId().equals(
                "api-endpoint://shop/_root/_api/OPTIONS:/health")));
    }

    @Test
    void preservesTypeLevelRequestMappingHttpMethods() throws IOException {
        write("src/main/java/com/acme/web/SearchController.java", """
                package com.acme.web;

                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RequestMethod;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequestMapping(path = "/api/search", method = RequestMethod.GET)
                class SearchController {
                    @RequestMapping("/users")
                    String users() { return "ok"; }
                }
                """);
        SpringMvcAnalysisResult result = SpringMvcAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/java/com/acme/web/SearchController.java")));

        JavaSourceFactBatch batch = SpringMvcFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/java",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/java"));

        assertFact(batch,
                "ROUTES_TO",
                "api-endpoint://shop/_root/_api/GET:/api/search/users",
                "method://shop/_root/src/main/java/com.acme.web.SearchController#users()Ljava/lang/String;");
        assertFalse(batch.facts().stream().anyMatch(fact -> fact.sourceIdentityId().equals(
                "api-endpoint://shop/_root/_api/POST:/api/search/users")));
    }

    @Test
    void unionsTypeAndMethodRequestMappingHttpMethods() throws IOException {
        write("src/main/java/com/acme/web/ConstrainedController.java", """
                package com.acme.web;

                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RequestMethod;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequestMapping(path = "/api/constrained", method = RequestMethod.GET)
                class ConstrainedController {
                    @RequestMapping(path = "/users", method = {RequestMethod.GET, RequestMethod.POST})
                    String users() { return "ok"; }
                }
                """);
        SpringMvcAnalysisResult result = SpringMvcAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/java/com/acme/web/ConstrainedController.java")));

        JavaSourceFactBatch batch = SpringMvcFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/java",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/java"));

        assertFact(batch,
                "ROUTES_TO",
                "api-endpoint://shop/_root/_api/GET:/api/constrained/users",
                "method://shop/_root/src/main/java/com.acme.web.ConstrainedController#users()Ljava/lang/String;");
        assertFact(batch,
                "ROUTES_TO",
                "api-endpoint://shop/_root/_api/POST:/api/constrained/users",
                "method://shop/_root/src/main/java/com.acme.web.ConstrainedController#users()Ljava/lang/String;");
    }

    @Test
    void stripsRegexConstraintsFromSpringRoutePathVariables() throws IOException {
        write("src/main/java/com/acme/web/RegexController.java", """
                package com.acme.web;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequestMapping("/api/orders")
                class RegexController {
                    @GetMapping("/{id:\\\\d+}")
                    String show(String id) { return id; }
                }
                """);
        SpringMvcAnalysisResult result = SpringMvcAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/java/com/acme/web/RegexController.java")));

        JavaSourceFactBatch batch = SpringMvcFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/java",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/java"));

        assertFact(batch,
                "ROUTES_TO",
                "api-endpoint://shop/_root/_api/GET:/api/orders/{id}",
                "method://shop/_root/src/main/java/com.acme.web.RegexController#show(Ljava/lang/String;)Ljava/lang/String;");
        assertFact(batch,
                "DECLARES_ENTRYPOINT",
                "api-endpoint://shop/_root/_api/GET:/api/orders/{id}",
                "entrypoint://shop/_root/_entrypoints/spring/GET/api/orders/{id}");
    }

    @Test
    void stripsRegexConstraintsWithBraceQuantifiersFromSpringRoutePathVariables() throws IOException {
        write("src/main/java/com/acme/web/QuantifierController.java", """
                package com.acme.web;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequestMapping("/api/orders")
                class QuantifierController {
                    @GetMapping("/{id:[0-9]{2}}")
                    String show(String id) { return id; }
                }
                """);
        SpringMvcAnalysisResult result = SpringMvcAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/java/com/acme/web/QuantifierController.java")));

        JavaSourceFactBatch batch = SpringMvcFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/java",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/java"));

        assertFact(batch,
                "ROUTES_TO",
                "api-endpoint://shop/_root/_api/GET:/api/orders/{id}",
                "method://shop/_root/src/main/java/com.acme.web.QuantifierController#show(Ljava/lang/String;)Ljava/lang/String;");
    }

    @Test
    void keepsCommaQuantifiersInsideSpringRoutePathVariableRegexes() throws IOException {
        write("src/main/java/com/acme/web/CommaQuantifierController.java", """
                package com.acme.web;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequestMapping("/api/orders")
                class CommaQuantifierController {
                    @GetMapping("/{id:[0-9]{2,4}}")
                    String show(String id) { return id; }
                }
                """);
        SpringMvcAnalysisResult result = SpringMvcAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/java/com/acme/web/CommaQuantifierController.java")));

        JavaSourceFactBatch batch = SpringMvcFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/java",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/java"));

        assertFact(batch,
                "ROUTES_TO",
                "api-endpoint://shop/_root/_api/GET:/api/orders/{id}",
                "method://shop/_root/src/main/java/com.acme.web.CommaQuantifierController#show(Ljava/lang/String;)Ljava/lang/String;");
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
