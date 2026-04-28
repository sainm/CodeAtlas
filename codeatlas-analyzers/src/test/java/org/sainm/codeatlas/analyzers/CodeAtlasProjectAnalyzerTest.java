package org.sainm.codeatlas.analyzers;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SymbolKind;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeAtlasProjectAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void analyzesMixedJavaWebProjectMvpEdges() throws Exception {
        write("src/main/java/com/acme/UserController.java", """
            package com.acme;
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RestController;
            @RestController
            class UserController {
              @GetMapping("/users")
              String list() { return "ok"; }
            }
            """);
        write("src/main/java/com/acme/UserAction.java", """
            package com.acme;
            class UserAction {
              void execute(javax.servlet.http.HttpServletRequest request) {
                request.getParameter("name");
              }
            }
            """);
        write("src/main/webapp/WEB-INF/struts-config.xml", """
            <struts-config>
              <action-mappings>
                <action path="/user/save" type="com.acme.UserAction" input="/user/edit.jsp"/>
              </action-mappings>
            </struts-config>
            """);
        write("src/main/webapp/WEB-INF/web.xml", """
            <web-app>
              <servlet>
                <servlet-name>action</servlet-name>
                <servlet-class>org.apache.struts.action.ActionServlet</servlet-class>
                <init-param>
                  <param-name>config</param-name>
                  <param-value>/WEB-INF/struts-config-admin.xml</param-value>
                </init-param>
              </servlet>
              <servlet-mapping>
                <servlet-name>action</servlet-name>
                <url-pattern>*.do</url-pattern>
              </servlet-mapping>
            </web-app>
            """);
        write("src/main/webapp/WEB-INF/struts-config-admin.xml", """
            <struts-config>
              <action-mappings>
                <action path="/admin/save" type="com.acme.AdminAction"/>
              </action-mappings>
            </struts-config>
            """);
        write("src/main/webapp/user/edit.jsp", """
            <html:form action="/user/save.do">
              <html:text property="name"/>
            </html:form>
            """);
        write("src/main/resources/com/acme/UserMapper.xml", """
            <mapper namespace="com.acme.UserMapper">
              <select id="findAll">select id, name from users</select>
            </mapper>
            """);
        write("src/main/resources/app.dicon", """
            <components>
              <component name="userService" class="com.acme.service.UserService"/>
            </components>
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "project", tempDir);
        ProjectAnalysisResult result = new CodeAtlasProjectAnalyzer().analyze(scope, "shop");

        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.API_ENDPOINT));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.ACTION_PATH));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.ACTION_PATH
            && node.symbolId().ownerQualifiedName().equals("admin/save")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.JSP_INPUT));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.REQUEST_PARAMETER));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.SQL_STATEMENT));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().value().contains("seasar:userService")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.ROUTES_TO));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.SUBMITS_TO));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.BINDS_TO));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.READS_TABLE));
    }

    private void write(String relativePath, String content) throws Exception {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
