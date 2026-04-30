package org.sainm.codeatlas.analyzers.jsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebAppContextBuilderTest {
    @TempDir
    Path tempDir;

    @Test
    void buildsWebAppContextFromWebXmlAndTlds() throws Exception {
        Path webXml = tempDir.resolve("WEB-INF/web.xml");
        Files.createDirectories(webXml.getParent());
        Files.writeString(webXml, """
            <web-app version="2.4">
              <jsp-config>
                <taglib>
                  <taglib-uri>/WEB-INF/struts-html.tld</taglib-uri>
                  <taglib-location>/WEB-INF/struts-html.tld</taglib-location>
                </taglib>
                <jsp-property-group>
                  <url-pattern>*.jsp</url-pattern>
                  <page-encoding>Windows-31J</page-encoding>
                </jsp-property-group>
              </jsp-config>
            </web-app>
            """);
        Files.writeString(tempDir.resolve("WEB-INF/struts-html.tld"), """
            <taglib>
              <uri>http://struts.apache.org/tags-html</uri>
            </taglib>
            """);
        Files.createDirectories(tempDir.resolve("WEB-INF/classes"));
        Files.createDirectories(tempDir.resolve("WEB-INF/tags/app"));
        Files.writeString(tempDir.resolve("WEB-INF/tags/app/field.tag"), "<%@ tag body-content=\"empty\" %>");
        Files.writeString(tempDir.resolve("WEB-INF/tags/app/panel.tagx"), "<jsp:root/>");
        Path lib = tempDir.resolve("WEB-INF/lib");
        Files.createDirectories(lib);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(lib.resolve("struts-tags.jar")))) {
            output.putNextEntry(new JarEntry("META-INF/struts-bean.tld"));
            output.write("""
                <taglib>
                  <uri>http://struts.apache.org/tags-bean</uri>
                </taglib>
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
        Files.createDirectories(tempDir.resolve("build/classes/java/main"));
        Files.createDirectories(tempDir.resolve("build/resources/main"));
        Files.createDirectories(tempDir.resolve("target/classes"));

        WebAppContext context = new WebAppContextBuilder().build(tempDir);

        assertEquals("2.4", context.servletVersion());
        assertEquals("2.0", context.jspVersion());
        assertEquals("Windows-31J", context.defaultEncoding());
        assertTrue(context.taglibs().containsKey("/WEB-INF/struts-html.tld"));
        assertEquals("WEB-INF/struts-html.tld", context.taglibs().get("http://struts.apache.org/tags-html"));
        assertEquals(
            "WEB-INF/lib/struts-tags.jar!/META-INF/struts-bean.tld",
            context.taglibs().get("http://struts.apache.org/tags-bean")
        );
        assertEquals("WEB-INF/tags/app/field.tag", context.tagFiles().get("app/field"));
        assertEquals("WEB-INF/tags/app/panel.tagx", context.tagFiles().get("app/panel"));
        assertTrue(context.classpathEntries().stream().anyMatch(path -> path.endsWith("classes")));
        assertTrue(context.classpathEntries().stream().anyMatch(path -> path.endsWith(Path.of("build/classes/java/main"))));
        assertTrue(context.classpathEntries().stream().anyMatch(path -> path.endsWith(Path.of("build/resources/main"))));
        assertTrue(context.classpathEntries().stream().anyMatch(path -> path.endsWith(Path.of("target/classes"))));
    }
}
