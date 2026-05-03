package org.sainm.codeatlas.analyzers.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpringMvcAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsControllersRoutesServicesAndInjectionHintsWithoutClasspath() throws IOException {
        write("src/main/java/com/acme/web/OrderController.java", """
                package com.acme.web;

                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.stereotype.Service;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.PathVariable;
                import org.springframework.web.bind.annotation.PostMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequestMapping("/api/orders")
                class OrderController {
                    @Autowired
                    private OrderService service;

                    OrderController(AuditService auditService) {}

                    @GetMapping("/{id}")
                    OrderDto show(@PathVariable String id) { return service.find(id); }

                    @PostMapping(path = "/")
                    void create() {}
                }

                @Service
                class OrderService {
                    OrderDto find(String id) { return new OrderDto(); }
                }

                class AuditService {}
                class OrderDto {}
                """);

        SpringMvcAnalysisResult result = SpringMvcAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/java/com/acme/web/OrderController.java")));

        assertTrue(result.noClasspathFallbackUsed());
        assertTrue(result.components().stream().anyMatch(component -> component.qualifiedName().equals("com.acme.web.OrderController")
                && component.kind() == SpringComponentKind.REST_CONTROLLER));
        assertTrue(result.components().stream().anyMatch(component -> component.qualifiedName().equals("com.acme.web.OrderService")
                && component.kind() == SpringComponentKind.SERVICE));
        assertTrue(result.routes().stream().anyMatch(route -> route.ownerQualifiedName().equals("com.acme.web.OrderController")
                && route.methodName().equals("show")
                && route.httpMethods().equals(List.of("GET"))
                && route.paths().equals(List.of("/api/orders/{id}"))));
        assertTrue(result.routes().stream().anyMatch(route -> route.methodName().equals("create")
                && route.httpMethods().equals(List.of("POST"))
                && route.paths().equals(List.of("/api/orders/"))));
        assertTrue(result.injections().stream().anyMatch(injection -> injection.kind() == SpringInjectionKind.FIELD
                && injection.ownerQualifiedName().equals("com.acme.web.OrderController")
                && injection.memberName().equals("service")
                && injection.targetTypeName().equals("com.acme.web.OrderService")));
        assertTrue(result.injections().stream().anyMatch(injection -> injection.kind() == SpringInjectionKind.CONSTRUCTOR
                && injection.ownerQualifiedName().equals("com.acme.web.OrderController")
                && injection.memberName().equals("<init>")
                && injection.targetTypeName().equals("com.acme.web.AuditService")));
        assertFalse(result.diagnostics().isEmpty());
    }

    @Test
    void extractsRequestMappingMethodVariantsAndMultiplePaths() throws IOException {
        write("src/main/java/com/acme/web/AdminController.java", """
                package com.acme.web;

                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RequestMethod;
                import org.springframework.stereotype.Controller;

                @Controller
                @RequestMapping({"/admin", "/ops"})
                class AdminController {
                    @RequestMapping(value = {"/users", "/accounts"}, method = {RequestMethod.GET, RequestMethod.POST})
                    void users() {}
                }
                """);

        SpringMvcAnalysisResult result = SpringMvcAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/java/com/acme/web/AdminController.java")));

        SpringRouteInfo route = result.routes().stream()
                .filter(candidate -> candidate.methodName().equals("users"))
                .findFirst()
                .orElseThrow();

        assertEquals(List.of("GET", "POST"), route.httpMethods());
        assertEquals(List.of("/admin/users", "/admin/accounts", "/ops/users", "/ops/accounts"), route.paths());
    }

    @Test
    void ignoresUnannotatedConstructorsWhenAnotherConstructorIsAnnotated() throws IOException {
        write("src/main/java/com/acme/web/MultiConstructorController.java", """
                package com.acme.web;

                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                class MultiConstructorController {
                    MultiConstructorController(AuditService auditService) {}

                    @Autowired
                    MultiConstructorController(OrderService orderService) {}
                }

                class AuditService {}
                class OrderService {}
                """);

        SpringMvcAnalysisResult result = SpringMvcAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/java/com/acme/web/MultiConstructorController.java")));

        assertTrue(result.injections().stream().anyMatch(injection -> injection.kind() == SpringInjectionKind.CONSTRUCTOR
                && injection.targetTypeName().equals("com.acme.web.OrderService")));
        assertFalse(result.injections().stream().anyMatch(injection -> injection.kind() == SpringInjectionKind.CONSTRUCTOR
                && injection.targetTypeName().equals("com.acme.web.AuditService")));
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
