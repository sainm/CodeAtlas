package org.sainm.codeatlas.analyzers.struts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StrutsWebXmlAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsActionServletConfigsAndMappings() throws Exception {
        Path webXml = tempDir.resolve("WEB-INF/web.xml");
        Files.createDirectories(webXml.getParent());
        Files.writeString(webXml, """
            <web-app>
              <servlet>
                <servlet-name>action</servlet-name>
                <servlet-class>org.apache.struts.action.ActionServlet</servlet-class>
                <init-param>
                  <param-name>config</param-name>
                  <param-value>/WEB-INF/struts-config.xml, /WEB-INF/struts-config-admin.xml</param-value>
                </init-param>
              </servlet>
              <servlet-mapping>
                <servlet-name>action</servlet-name>
                <url-pattern>*.do</url-pattern>
              </servlet-mapping>
            </web-app>
            """);

        StrutsWebXmlAnalysisResult result = new StrutsWebXmlAnalyzer().analyze(webXml);

        assertEquals("action", result.servletNames().getFirst());
        assertEquals(2, result.configLocations().size());
        assertEquals("/WEB-INF/struts-config-admin.xml", result.configLocations().get(1));
        assertEquals("*.do", result.urlPatterns().getFirst());
    }
}
