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

class SeasarDiconAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void parsesDiconComponentsIncludesPropertiesAspectsAndInterceptors() throws IOException {
        write("WEB-INF/app.dicon", """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE components PUBLIC "-//SEASAR//DTD S2Container 2.4//EN"
                  "http://www.seasar.org/dtd/components24.dtd">
                <components namespace="app">
                  <include path="dao.dicon"/>
                  <component name="userService"
                             class="com.acme.service.UserService"
                             interface="com.acme.service.UserServiceApi"
                             autoBinding="auto">
                    <property name="userDao">userDao</property>
                    <aspect pointcut="get.*">traceInterceptor</aspect>
                  </component>
                  <component name="traceInterceptor"
                             class="org.seasar.framework.aop.interceptors.TraceInterceptor"/>
                  <component name="orderLogic"/>
                </components>
                """);

        SeasarDiconAnalysisResult result = SeasarDiconAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("WEB-INF/app.dicon")));

        assertTrue(result.diagnostics().isEmpty());
        assertEquals(List.of("app"), result.namespaces().stream()
                .map(SeasarDiconNamespaceInfo::namespace)
                .toList());
        SeasarDiconComponentInfo userService = result.components().stream()
                .filter(component -> component.name().equals("userService"))
                .findFirst()
                .orElseThrow();
        assertEquals("WEB-INF/app.dicon", userService.diconPath());
        assertEquals("app", userService.namespace());
        assertEquals("com.acme.service.UserService", userService.className());
        assertEquals("com.acme.service.UserServiceApi", userService.interfaceName());
        assertEquals("auto", userService.autoBinding());
        assertTrue(result.includes().stream().anyMatch(include -> include.path().equals("dao.dicon")));
        assertTrue(result.properties().stream().anyMatch(property -> property.componentName().equals("userService")
                && property.name().equals("userDao")
                && property.expression().equals("userDao")));
        assertTrue(result.aspects().stream().anyMatch(aspect -> aspect.componentName().equals("userService")
                && aspect.pointcut().equals("get.*")
                && aspect.interceptor().equals("traceInterceptor")));
        assertTrue(result.interceptors().stream().anyMatch(interceptor -> interceptor.name().equals("traceInterceptor")
                && interceptor.className().endsWith("TraceInterceptor")));
        assertTrue(result.components().stream().anyMatch(component -> component.name().equals("orderLogic")
                && component.namingCandidate()));
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
