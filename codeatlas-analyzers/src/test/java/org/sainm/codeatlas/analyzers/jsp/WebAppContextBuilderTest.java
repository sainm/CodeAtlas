package org.sainm.codeatlas.analyzers.jsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
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
              </jsp-config>
            </web-app>
            """);
        Files.writeString(tempDir.resolve("WEB-INF/struts-html.tld"), "<taglib/>");
        Files.createDirectories(tempDir.resolve("WEB-INF/classes"));

        WebAppContext context = new WebAppContextBuilder().build(tempDir);

        assertEquals("2.4", context.servletVersion());
        assertTrue(context.taglibs().containsKey("/WEB-INF/struts-html.tld"));
        assertTrue(context.classpathEntries().stream().anyMatch(path -> path.endsWith("classes")));
    }
}
