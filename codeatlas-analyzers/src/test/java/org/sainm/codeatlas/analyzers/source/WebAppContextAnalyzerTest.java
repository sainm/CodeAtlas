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

class WebAppContextAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void buildsWebAppContextFromWebXmlAndWebInfResources() throws IOException {
        write("src/main/webapp/WEB-INF/web.xml", """
                <web-app version="3.0">
                  <jsp-config>
                    <jsp-property-group>
                      <url-pattern>*.jsp</url-pattern>
                      <page-encoding>UTF-8</page-encoding>
                    </jsp-property-group>
                  </jsp-config>
                  <servlet>
                    <servlet-name>action</servlet-name>
                    <servlet-class>org.apache.struts.action.ActionServlet</servlet-class>
                    <init-param>
                      <param-name>config/admin</param-name>
                      <param-value>/WEB-INF/struts-config-admin.xml</param-value>
                    </init-param>
                  </servlet>
                  <servlet-mapping>
                    <servlet-name>action</servlet-name>
                    <url-pattern>*.do</url-pattern>
                  </servlet-mapping>
                </web-app>
                """);
        write("src/main/webapp/WEB-INF/app.tld", "<taglib/>");
        write("src/main/webapp/WEB-INF/tags/card.tag", "<%@ tag body-content=\"scriptless\" %>");
        writeBytes("src/main/webapp/WEB-INF/lib/struts.jar", new byte[] {1, 2, 3});
        Files.createDirectories(tempDir.resolve("src/main/webapp/WEB-INF/classes"));

        WebAppContext context = WebAppContextAnalyzer.defaults().analyze(tempDir.resolve("src/main/webapp"));

        assertEquals("src/main/webapp", context.webRoot());
        assertEquals("src/main/webapp/WEB-INF/web.xml", context.webXmlPath());
        assertEquals("3.0", context.servletVersion());
        assertTrue(context.pageEncodings().stream().anyMatch(encoding -> encoding.urlPattern().equals("*.jsp")
                && encoding.encoding().equals("UTF-8")));
        assertEquals(List.of("src/main/webapp/WEB-INF/app.tld"), context.tldFiles());
        assertEquals(List.of("src/main/webapp/WEB-INF/tags/card.tag"), context.tagFiles());
        assertEquals(List.of("src/main/webapp/WEB-INF/lib/struts.jar"), context.webInfLibJars());
        assertTrue(context.classpathCandidates().contains("src/main/webapp/WEB-INF/classes"));
        assertTrue(context.classpathCandidates().contains("src/main/webapp/WEB-INF/lib/struts.jar"));
        assertTrue(context.strutsConfigPaths().contains("/WEB-INF/struts-config-admin.xml"));
        assertTrue(context.servletMappings().stream().anyMatch(mapping -> mapping.servletName().equals("action")
                && mapping.urlPattern().equals("*.do")));
        assertTrue(context.diagnostics().isEmpty());
    }

    @Test
    void splitsCommaSeparatedStrutsConfigInitParamValues() throws IOException {
        write("src/main/webapp/WEB-INF/web.xml", """
                <web-app>
                  <servlet>
                    <servlet-name>action</servlet-name>
                    <servlet-class>org.apache.struts.action.ActionServlet</servlet-class>
                    <init-param>
                      <param-name>config</param-name>
                      <param-value>/WEB-INF/struts-config.xml, /WEB-INF/struts-config-admin.xml</param-value>
                    </init-param>
                  </servlet>
                </web-app>
                """);

        WebAppContext context = WebAppContextAnalyzer.defaults().analyze(tempDir.resolve("src/main/webapp"));

        assertEquals(List.of("/WEB-INF/struts-config.xml", "/WEB-INF/struts-config-admin.xml"),
                context.strutsConfigPaths());
    }

    @Test
    void usesDefaultStrutsConfigWhenActionServletOmitsConfigParam() throws Exception {
        write("src/main/webapp/WEB-INF/web.xml", """
                <web-app>
                  <servlet>
                    <servlet-name>action</servlet-name>
                    <servlet-class>org.apache.struts.action.ActionServlet</servlet-class>
                  </servlet>
                </web-app>
                """);
        write("src/main/webapp/WEB-INF/struts-config.xml", """
                <struts-config>
                  <action-mappings>
                    <action path="/help" type="com.acme.web.HelpAction"/>
                  </action-mappings>
                </struts-config>
                """);

        WebAppContext context = WebAppContextAnalyzer.defaults().analyze(tempDir.resolve("src/main/webapp"));
        StrutsConfigAnalysisResult result = (StrutsConfigAnalysisResult) StrutsConfigAnalyzer.class
                .getMethod("analyze", Path.class, WebAppContext.class)
                .invoke(StrutsConfigAnalyzer.defaults(), tempDir.resolve("src/main/webapp"), context);

        assertEquals(List.of("/WEB-INF/struts-config.xml"), context.strutsConfigPaths());
        assertTrue(result.actions().stream().anyMatch(action -> action.path().equals("/help")));
    }

    @Test
    void preservesStrutsModuleKeysFromWebXmlConfigParameters() throws Exception {
        write("src/main/webapp/WEB-INF/web.xml", """
                <web-app>
                  <servlet>
                    <servlet-name>action</servlet-name>
                    <servlet-class>org.apache.struts.action.ActionServlet</servlet-class>
                    <init-param>
                      <param-name>config/admin</param-name>
                      <param-value>/WEB-INF/module.xml</param-value>
                    </init-param>
                  </servlet>
                </web-app>
                """);
        write("src/main/webapp/WEB-INF/module.xml", """
                <struts-config>
                  <action-mappings>
                    <action path="/save" type="com.acme.web.SaveAction"/>
                  </action-mappings>
                </struts-config>
                """);

        WebAppContext context = WebAppContextAnalyzer.defaults().analyze(tempDir.resolve("src/main/webapp"));
        List<?> strutsConfigs = (List<?>) WebAppContext.class.getMethod("strutsConfigs").invoke(context);
        Object config = strutsConfigs.get(0);

        assertEquals("admin", config.getClass().getMethod("moduleKey").invoke(config));
        assertEquals("/WEB-INF/module.xml", config.getClass().getMethod("path").invoke(config));

        StrutsConfigAnalysisResult result = (StrutsConfigAnalysisResult) StrutsConfigAnalyzer.class
                .getMethod("analyze", Path.class, WebAppContext.class)
                .invoke(StrutsConfigAnalyzer.defaults(), tempDir.resolve("src/main/webapp"), context);

        assertTrue(result.actions().stream().anyMatch(action -> action.path().equals("/admin/save")));
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private void writeBytes(String relativePath, byte[] content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.write(file, content);
    }
}
