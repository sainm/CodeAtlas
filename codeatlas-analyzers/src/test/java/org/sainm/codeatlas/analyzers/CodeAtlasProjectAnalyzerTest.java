package org.sainm.codeatlas.analyzers;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.NodeRole;
import org.sainm.codeatlas.graph.model.SymbolKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;
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
              private final UserService service = new UserService();
              void execute(javax.servlet.http.HttpServletRequest request) {
                String rawName = request.getParameter("name");
                String name = rawName;
                String finalName = name;
                service.save(finalName);
              }
            }
            class UserService {
              private final UserDao dao = new UserDao();
              void save(String name) {
                String outgoing = name;
                dao.insert(outgoing);
              }
            }
            class UserDao {
              void insert(String name) throws Exception {
                java.sql.Connection connection = null;
                java.sql.PreparedStatement statement = connection.prepareStatement("update users set name = ? where id = 1");
                statement.setString(1, name);
                statement.executeUpdate();
              }
            }
            """);
        write("src/main/java/com/acme/UserEntity.java", """
            package com.acme;
            import jakarta.persistence.Column;
            import jakarta.persistence.Entity;
            import jakarta.persistence.Id;
            import jakarta.persistence.Table;
            @Entity
            @Table(name = "users")
            class UserEntity {
              @Id
              @Column(name = "id")
              private long id;
              @Column(name = "name")
              private String name;
            }
            """);
        write("src/main/webapp/WEB-INF/struts-config.xml", """
            <struts-config>
              <message-resources parameter="com.acme.ApplicationResources"/>
              <global-forwards>
                <forward name="login" path="/login.jsp"/>
              </global-forwards>
              <global-exceptions>
                <exception key="error.system" type="com.acme.SystemException" path="/error.jsp"/>
              </global-exceptions>
              <action-mappings>
                <action path="/user/save" type="com.acme.UserAction" input="/user/edit.jsp"/>
              </action-mappings>
            </struts-config>
            """);
        write("src/main/webapp/WEB-INF/tiles-defs.xml", """
            <tiles-definitions>
              <definition name="user.detail" path="/WEB-INF/layouts/base.jsp">
                <put name="body" value="/WEB-INF/jsp/user/detail.jsp"/>
              </definition>
            </tiles-definitions>
            """);
        write("src/main/webapp/WEB-INF/validation.xml", """
            <form-validation>
              <formset>
                <form name="userForm">
                  <field property="name" depends="required"/>
                </form>
              </formset>
            </form-validation>
            """);
        write("src/main/webapp/WEB-INF/web.xml", """
            <web-app>
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
        write("src/main/webapp/WEB-INF/struts-config-admin.xml", """
            <struts-config>
              <action-mappings>
                <action path="/save" type="com.acme.AdminAction"/>
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
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.PASSES_PARAM
            && fact.factKey().source().ownerQualifiedName().equals("com.acme.UserAction")
            && fact.factKey().target().ownerQualifiedName().equals("com.acme.UserService")
            && fact.factKey().qualifier().contains("request-parameter-local:name argument:finalName")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.PASSES_PARAM
            && fact.factKey().source().ownerQualifiedName().equals("com.acme.UserService")
            && fact.factKey().target().ownerQualifiedName().equals("com.acme.UserDao")
            && fact.factKey().qualifier().contains("method-parameter:outgoing")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.READS_TABLE));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.PASSES_PARAM
            && fact.factKey().source().ownerQualifiedName().equals("com.acme.UserDao")
            && fact.factKey().source().memberName().equals("insert")
            && fact.factKey().target().kind() == SymbolKind.SQL_STATEMENT
            && fact.factKey().qualifier().equals("jdbc-parameter:1:name")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.WRITES_TABLE
            && fact.factKey().source().kind() == SymbolKind.SQL_STATEMENT
            && fact.factKey().target().kind() == SymbolKind.DB_TABLE
            && fact.factKey().target().ownerQualifiedName().equals("users")
            && fact.factKey().qualifier().equals("jdbc-table:users")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.BINDS_TO
            && fact.factKey().source().kind() == SymbolKind.CLASS
            && fact.factKey().source().ownerQualifiedName().equals("com.acme.UserEntity")
            && fact.factKey().target().kind() == SymbolKind.DB_TABLE
            && fact.factKey().target().ownerQualifiedName().equals("users")
            && fact.factKey().qualifier().equals("jpa-entity-table:users")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.BINDS_TO
            && fact.factKey().source().kind() == SymbolKind.FIELD
            && fact.factKey().source().memberName().equals("name")
            && fact.factKey().target().kind() == SymbolKind.DB_COLUMN
            && fact.factKey().target().localId().equals("name")
            && fact.factKey().qualifier().equals("jpa-field-column:users.name")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.USES_CONFIG
            && fact.factKey().qualifier().equals("message-resources:com.acme.ApplicationResources")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().qualifier().equals("global-forward:login")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().qualifier().equals("exception-path:error.system")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().qualifier().equals("tiles-put-value:body")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.COVERED_BY
            && fact.factKey().qualifier().equals("name")));
    }

    @Test
    void analyzesStruts1JspSampleExtendedCoverage() throws Exception {
        Path sampleRoot = Path.of("..", "samples", "struts1-jsp").toAbsolutePath().normalize();
        assertTrue(Files.exists(sampleRoot.resolve("src/main/webapp/WEB-INF/web.xml")));

        AnalyzerScope scope = new AnalyzerScope("struts-sample", "_root", "snapshot-1", "run-1", "project", sampleRoot);
        ProjectAnalysisResult result = new CodeAtlasProjectAnalyzer().analyze(scope, "struts-sample");

        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.ACTION_PATH
            && node.symbolId().ownerQualifiedName().equals("user/save")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.ACTION_PATH
            && node.symbolId().ownerQualifiedName().equals("admin/user/list")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.USES_CONFIG
            && fact.factKey().qualifier().equals("controller-processorClass")
            && fact.factKey().target().ownerQualifiedName().equals("com.acme.legacy.web.LegacyRequestProcessor")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.USES_CONFIG
            && fact.factKey().qualifier().equals("message-resources:com.acme.legacy.AdminResources")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.USES_CONFIG
            && fact.factKey().qualifier().startsWith("plugin-property:pathnames=")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().qualifier().equals("global-forward:adminHome")
            && fact.factKey().target().kind() == SymbolKind.ACTION_PATH
            && fact.factKey().target().ownerQualifiedName().equals("admin/user/list")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.ROUTES_TO
            && fact.factKey().source().kind() == SymbolKind.ACTION_PATH
            && fact.factKey().source().ownerQualifiedName().equals("admin/user/list")
            && fact.factKey().target().kind() == SymbolKind.METHOD
            && fact.factKey().target().memberName().equals("list")
            && fact.factKey().qualifier().equals("dispatch-method-candidate:method:list")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.ROUTES_TO
            && fact.factKey().source().kind() == SymbolKind.ACTION_PATH
            && fact.factKey().source().ownerQualifiedName().equals("admin/user/list")
            && fact.factKey().target().kind() == SymbolKind.METHOD
            && fact.factKey().target().memberName().equals("search")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.ROUTES_TO
            && fact.factKey().source().kind() == SymbolKind.ACTION_PATH
            && fact.factKey().source().ownerQualifiedName().equals("admin/user/list")
            && fact.factKey().target().kind() == SymbolKind.METHOD
            && fact.factKey().target().memberName().equals("search")
            && fact.factKey().qualifier().equals("lookup-dispatch-method:button.search:search")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().qualifier().equals("forward:success")
            && fact.factKey().target().kind() == SymbolKind.CONFIG_KEY
            && fact.factKey().target().localId().equals("tiles-definition:admin.user.list")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().source().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().kind() == SymbolKind.ACTION_PATH
            && fact.factKey().target().ownerQualifiedName().equals("user/save")
            && fact.factKey().qualifier().equals("html-link:/user/save.do")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().source().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().ownerQualifiedName().equals("user/detail.jsp")
            && fact.factKey().qualifier().equals("html-link:/user/detail.jsp")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.INCLUDES
            && fact.factKey().source().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().ownerQualifiedName().endsWith("samples/struts1-jsp/src/main/webapp/common/footer.jsp")
            && fact.factKey().qualifier().equals("static_directive:/common/footer.jsp")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().qualifier().equals("exception-path:error.admin.search")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.BINDS_TO
            && fact.factKey().source().kind() == SymbolKind.REQUEST_PARAMETER
            && fact.factKey().source().ownerQualifiedName().equals("includeDisabled")
            && fact.factKey().target().localId().contains("form-bean:adminSearchForm:property:includeDisabled")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.EXTENDS
            && fact.factKey().qualifier().equals("tiles-extends")
            && fact.factKey().target().localId().contains("tiles-definition:admin.base")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.COVERED_BY
            && fact.factKey().qualifier().equals("includeDisabled")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.WRITES_PARAM
            && fact.factKey().source().kind() == SymbolKind.JSP_INPUT
            && fact.factKey().qualifier().equals("includeDisabled")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.WRITES_PARAM
            && fact.factKey().source().kind() == SymbolKind.JSP_INPUT
            && fact.factKey().source().localId().contains("method")
            && fact.factKey().target().kind() == SymbolKind.REQUEST_PARAMETER
            && fact.factKey().target().ownerQualifiedName().equals("method")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.CLASS
            && node.symbolId().ownerQualifiedName().equals("com.acme.legacy.logic.SaveUserLogic")
            && node.roles().contains(NodeRole.BUSINESS_LOGIC)));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.ROUTES_TO
            && fact.factKey().source().kind() == SymbolKind.ACTION_PATH
            && fact.factKey().source().ownerQualifiedName().equals("user/save")
            && fact.factKey().target().kind() == SymbolKind.METHOD
            && fact.factKey().target().ownerQualifiedName().equals("com.acme.legacy.web.UserAction")
            && fact.factKey().target().memberName().equals("execute")
            && fact.factKey().qualifier().equals("struts-action-execute")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.CALLS
            && fact.factKey().source().ownerQualifiedName().equals("com.acme.legacy.web.UserAction")
            && fact.factKey().source().memberName().equals("execute")
            && fact.factKey().target().ownerQualifiedName().equals("com.acme.legacy.logic.SaveUserLogic")
            && fact.factKey().target().memberName().equals("execute")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.CALLS
            && fact.factKey().source().ownerQualifiedName().equals("com.acme.legacy.logic.SaveUserLogic")
            && fact.factKey().source().memberName().equals("execute")
            && fact.factKey().target().ownerQualifiedName().equals("com.acme.legacy.service.UserService")
            && fact.factKey().target().memberName().equals("save")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.USES_CONFIG
            && fact.factKey().qualifier().equals("plugin-init-xml:configLocation=/WEB-INF/master-data.xml")
            && fact.factKey().target().kind() == SymbolKind.CONFIG_KEY
            && fact.factKey().target().ownerQualifiedName().endsWith("WEB-INF/master-data.xml")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.DECLARES
            && fact.factKey().target().kind() == SymbolKind.CONFIG_KEY
            && fact.factKey().target().localId().equals("xml-entry:code:status.active")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.WRITES_TABLE
            && fact.factKey().qualifier().equals("plugin-init-table:code_master")
            && fact.factKey().target().kind() == SymbolKind.DB_TABLE
            && fact.factKey().target().ownerQualifiedName().equals("code_master")
            && fact.confidence().name().equals("POSSIBLE")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.BINDS_TO
            && fact.factKey().source().ownerQualifiedName().equals("com.acme.legacy.dao.UserJdbcDao")
            && fact.factKey().source().memberName().equals("touchUser")
            && fact.factKey().target().kind() == SymbolKind.SQL_STATEMENT
            && fact.factKey().qualifier().equals("jdbc-sql:select")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.READS_TABLE
            && fact.factKey().target().kind() == SymbolKind.DB_TABLE
            && fact.factKey().target().ownerQualifiedName().equals("users")
            && fact.factKey().qualifier().equals("jdbc-table:users")));
    }

    @Test
    void indexesBusinessJarClassesSoSourceCallsCanResolveToBinaryNodes() throws Exception {
        write("src/main/java/com/acme/web/PricingAction.java", """
            package com.acme.web;
            import com.vendor.logic.PricingLogic;
            class PricingAction extends org.apache.struts.action.Action {
              private final PricingLogic pricingLogic = new PricingLogic();
              public org.apache.struts.action.ActionForward execute(
                  org.apache.struts.action.ActionMapping mapping,
                  org.apache.struts.action.ActionForm form,
                  javax.servlet.http.HttpServletRequest request,
                  javax.servlet.http.HttpServletResponse response) {
                pricingLogic.apply(request.getParameter("userId"));
                return null;
              }
            }
            """);
        write("src/main/webapp/WEB-INF/struts-config.xml", """
            <struts-config>
              <action-mappings>
                <action path="/pricing/apply" type="com.acme.web.PricingAction"/>
              </action-mappings>
            </struts-config>
            """);
        writeBusinessJar("src/main/webapp/WEB-INF/lib/pricing-logic.jar");

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "project", tempDir);
        ProjectAnalysisResult result = new CodeAtlasProjectAnalyzer().analyze(scope, "shop");

        String jarMethodValue = "method://shop/_root/src/main/java/com.vendor.logic.PricingLogic#apply(java.lang.String):void";
        String jarInternalMethodValue = "method://shop/_root/src/main/java/com.vendor.logic.PricingAudit#record(java.lang.String):void";
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().value().equals(jarMethodValue)
            && node.roles().contains(NodeRole.BUSINESS_LOGIC)));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.CALLS
            && fact.factKey().source().ownerQualifiedName().equals("com.acme.web.PricingAction")
            && fact.factKey().source().memberName().equals("execute")
            && fact.factKey().target().value().equals(jarMethodValue)));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.CALLS
            && fact.factKey().source().value().equals(jarMethodValue)
            && fact.factKey().target().value().equals(jarInternalMethodValue)
            && fact.factKey().qualifier().equals("bytecode-static")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.ROUTES_TO
            && fact.factKey().source().kind() == SymbolKind.ACTION_PATH
            && fact.factKey().source().ownerQualifiedName().equals("pricing/apply")
            && fact.factKey().target().kind() == SymbolKind.METHOD
            && fact.factKey().target().ownerQualifiedName().equals("com.acme.web.PricingAction")
            && fact.factKey().target().memberName().equals("execute")));
    }

    @Test
    void routesStrutsActionConfiguredToBusinessJarActionExecuteMethod() throws Exception {
        write("src/main/webapp/WEB-INF/struts-config.xml", """
            <struts-config>
              <action-mappings>
                <action path="/jar/save" type="com.vendor.web.JarSaveAction"/>
              </action-mappings>
            </struts-config>
            """);
        writeStrutsActionJar("src/main/webapp/WEB-INF/lib/jar-action.jar");

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "project", tempDir);
        ProjectAnalysisResult result = new CodeAtlasProjectAnalyzer().analyze(scope, "shop");

        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.METHOD
            && node.symbolId().ownerQualifiedName().equals("com.vendor.web.JarSaveAction")
            && node.symbolId().memberName().equals("execute")
            && node.symbolId().descriptor().contains("ActionMapping")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.ROUTES_TO
            && fact.factKey().source().kind() == SymbolKind.ACTION_PATH
            && fact.factKey().source().ownerQualifiedName().equals("jar/save")
            && fact.factKey().target().kind() == SymbolKind.METHOD
            && fact.factKey().target().ownerQualifiedName().equals("com.vendor.web.JarSaveAction")
            && fact.factKey().target().memberName().equals("execute")
            && fact.factKey().qualifier().equals("struts-action-execute")));
    }

    @Test
    void routesStrutsDispatchActionConfiguredToBusinessJarMethods() throws Exception {
        write("src/main/webapp/WEB-INF/struts-config.xml", """
            <struts-config>
              <action-mappings>
                <action path="/jar/dispatch" type="com.vendor.web.JarDispatchAction" parameter="method"/>
              </action-mappings>
            </struts-config>
            """);
        writeStrutsDispatchActionJar("src/main/webapp/WEB-INF/lib/jar-dispatch-action.jar");

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "project", tempDir);
        ProjectAnalysisResult result = new CodeAtlasProjectAnalyzer().analyze(scope, "shop");

        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.ROUTES_TO
            && fact.factKey().source().kind() == SymbolKind.ACTION_PATH
            && fact.factKey().source().ownerQualifiedName().equals("jar/dispatch")
            && fact.factKey().target().kind() == SymbolKind.METHOD
            && fact.factKey().target().ownerQualifiedName().equals("com.vendor.web.JarDispatchAction")
            && fact.factKey().target().memberName().equals("save")
            && fact.factKey().qualifier().equals("dispatch-method-candidate:method:save")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.ROUTES_TO
            && fact.factKey().source().ownerQualifiedName().equals("jar/dispatch")
            && fact.factKey().target().kind() == SymbolKind.METHOD
            && fact.factKey().target().ownerQualifiedName().equals("com.vendor.web.JarDispatchAction")
            && fact.factKey().target().memberName().equals("delete")
            && fact.factKey().qualifier().equals("dispatch-method-candidate:method:delete")));
        assertTrue(result.facts().stream().noneMatch(fact -> fact.factKey().relationType() == RelationType.ROUTES_TO
            && fact.factKey().source().ownerQualifiedName().equals("jar/dispatch")
            && fact.factKey().target().kind() == SymbolKind.METHOD
            && fact.factKey().target().ownerQualifiedName().equals("com.vendor.web.JarDispatchAction")
            && fact.factKey().target().memberName().equals("helper")));
    }

    @Test
    void bindsBusinessJarActionFormFieldsToRequestParameters() throws Exception {
        write("src/main/webapp/WEB-INF/struts-config.xml", """
            <struts-config>
              <form-beans>
                <form-bean name="jarUserForm" type="com.vendor.web.JarUserForm"/>
              </form-beans>
              <action-mappings>
                <action path="/jar/form" type="com.vendor.web.JarSaveAction" name="jarUserForm"/>
              </action-mappings>
            </struts-config>
            """);
        writeStrutsActionFormJar("src/main/webapp/WEB-INF/lib/jar-form.jar");

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "project", tempDir);
        ProjectAnalysisResult result = new CodeAtlasProjectAnalyzer().analyze(scope, "shop");

        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.BINDS_TO
            && fact.factKey().source().kind() == SymbolKind.ACTION_PATH
            && fact.factKey().source().ownerQualifiedName().equals("jar/form")
            && fact.factKey().target().kind() == SymbolKind.CLASS
            && fact.factKey().target().ownerQualifiedName().equals("com.vendor.web.JarUserForm")
            && fact.factKey().qualifier().equals("action-form:jarUserForm")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.BINDS_TO
            && fact.factKey().source().kind() == SymbolKind.REQUEST_PARAMETER
            && fact.factKey().source().ownerQualifiedName().equals("userId")
            && fact.factKey().target().kind() == SymbolKind.FIELD
            && fact.factKey().target().ownerQualifiedName().equals("com.vendor.web.JarUserForm")
            && fact.factKey().target().memberName().equals("userId")
            && fact.factKey().qualifier().equals("userId")
            && fact.sourceType().name().equals("ASM")));
        assertTrue(result.facts().stream().noneMatch(fact -> fact.factKey().relationType() == RelationType.BINDS_TO
            && fact.factKey().source().kind() == SymbolKind.REQUEST_PARAMETER
            && fact.factKey().target().kind() == SymbolKind.FIELD
            && fact.factKey().target().ownerQualifiedName().equals("com.vendor.web.JarUserForm")
            && fact.factKey().target().memberName().equals("ignored")));
    }

    @Test
    void tracksStrutsActionFormGetterValueToBusinessMethodArgument() throws Exception {
        write("src/main/java/com/acme/UserAction.java", """
            package com.acme;
            class UserAction extends org.apache.struts.action.Action {
              private final UserService service = new UserService();
              public org.apache.struts.action.ActionForward execute(
                  org.apache.struts.action.ActionMapping mapping,
                  org.apache.struts.action.ActionForm form,
                  javax.servlet.http.HttpServletRequest request,
                  javax.servlet.http.HttpServletResponse response) {
                UserForm userForm = (UserForm) form;
                String userId = userForm.getUserId();
                service.save(userId);
                return null;
              }
            }
            class UserForm extends org.apache.struts.action.ActionForm {
              private String userId;
              String getUserId() {
                return userId;
              }
            }
            class UserService {
              void save(String value) {
              }
            }
            """);
        write("src/main/webapp/WEB-INF/struts-config.xml", """
            <struts-config>
              <form-beans>
                <form-bean name="userForm" type="com.acme.UserForm"/>
              </form-beans>
              <action-mappings>
                <action path="/user/save" type="com.acme.UserAction" name="userForm"/>
              </action-mappings>
            </struts-config>
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "project", tempDir);
        ProjectAnalysisResult result = new CodeAtlasProjectAnalyzer().analyze(scope, "shop");

        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.READS_PARAM
            && fact.factKey().source().ownerQualifiedName().equals("com.acme.UserAction")
            && fact.factKey().source().memberName().equals("execute")
            && fact.factKey().target().kind() == SymbolKind.REQUEST_PARAMETER
            && fact.factKey().target().ownerQualifiedName().equals("userId")
            && fact.factKey().qualifier().equals("action-form-getter:userId")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.PASSES_PARAM
            && fact.factKey().source().ownerQualifiedName().equals("com.acme.UserAction")
            && fact.factKey().source().memberName().equals("execute")
            && fact.factKey().target().ownerQualifiedName().equals("com.acme.UserService")
            && fact.factKey().target().memberName().equals("save")
            && fact.factKey().qualifier().equals("action-form-getter:userId argument:userId")));
    }

    @Test
    void tracksStrutsDynaActionFormGetValueToBusinessMethodArgument() throws Exception {
        write("src/main/java/com/acme/UserAction.java", """
            package com.acme;
            class UserAction extends org.apache.struts.action.Action {
              private final UserService service = new UserService();
              public org.apache.struts.action.ActionForward execute(
                  org.apache.struts.action.ActionMapping mapping,
                  org.apache.struts.action.ActionForm form,
                  javax.servlet.http.HttpServletRequest request,
                  javax.servlet.http.HttpServletResponse response) {
                org.apache.struts.action.DynaActionForm userForm = (org.apache.struts.action.DynaActionForm) form;
                String userId = (String) userForm.get("userId");
                service.save(userId);
                return null;
              }
            }
            class UserService {
              void save(String value) {
              }
            }
            """);
        write("src/main/webapp/WEB-INF/struts-config.xml", """
            <struts-config>
              <form-beans>
                <form-bean name="userForm" type="org.apache.struts.action.DynaActionForm">
                  <form-property name="userId" type="java.lang.String"/>
                </form-bean>
              </form-beans>
              <action-mappings>
                <action path="/user/save" type="com.acme.UserAction" name="userForm"/>
              </action-mappings>
            </struts-config>
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "project", tempDir);
        ProjectAnalysisResult result = new CodeAtlasProjectAnalyzer().analyze(scope, "shop");

        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.BINDS_TO
            && fact.factKey().source().kind() == SymbolKind.REQUEST_PARAMETER
            && fact.factKey().source().ownerQualifiedName().equals("userId")
            && fact.factKey().target().kind() == SymbolKind.CONFIG_KEY
            && fact.factKey().target().localId().contains("form-bean:userForm:property:userId")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.READS_PARAM
            && fact.factKey().source().ownerQualifiedName().equals("com.acme.UserAction")
            && fact.factKey().source().memberName().equals("execute")
            && fact.factKey().target().kind() == SymbolKind.REQUEST_PARAMETER
            && fact.factKey().target().ownerQualifiedName().equals("userId")
            && fact.factKey().qualifier().equals("action-form-get:userId")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.PASSES_PARAM
            && fact.factKey().source().ownerQualifiedName().equals("com.acme.UserAction")
            && fact.factKey().source().memberName().equals("execute")
            && fact.factKey().target().ownerQualifiedName().equals("com.acme.UserService")
            && fact.factKey().target().memberName().equals("save")
            && fact.factKey().qualifier().equals("action-form-get:userId argument:userId")));
    }

    @Test
    void linksStrutsActionMethodFindForwardToConfiguredForwardTarget() throws Exception {
        write("src/main/java/com/acme/UserAction.java", """
            package com.acme;
            class UserAction extends org.apache.struts.action.Action {
              public org.apache.struts.action.ActionForward execute(
                  org.apache.struts.action.ActionMapping mapping,
                  org.apache.struts.action.ActionForm form,
                  javax.servlet.http.HttpServletRequest request,
                  javax.servlet.http.HttpServletResponse response) {
                return mapping.findForward("success");
              }
            }
            """);
        write("src/main/webapp/WEB-INF/struts-config.xml", """
            <struts-config>
              <action-mappings>
                <action path="/user/save" type="com.acme.UserAction">
                  <forward name="success" path="/user/detail.jsp"/>
                </action>
              </action-mappings>
            </struts-config>
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "project", tempDir);
        ProjectAnalysisResult result = new CodeAtlasProjectAnalyzer().analyze(scope, "shop");

        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().source().kind() == SymbolKind.METHOD
            && fact.factKey().source().ownerQualifiedName().equals("com.acme.UserAction")
            && fact.factKey().source().memberName().equals("execute")
            && fact.factKey().target().kind() == SymbolKind.CONFIG_KEY
            && fact.factKey().target().ownerQualifiedName().equals("struts-forward")
            && fact.factKey().target().localId().equals("success")
            && fact.factKey().qualifier().equals("mapping.findForward:success")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().source().kind() == SymbolKind.CONFIG_KEY
            && fact.factKey().source().ownerQualifiedName().equals("struts-forward")
            && fact.factKey().source().localId().equals("success")
            && fact.factKey().target().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().ownerQualifiedName().equals("user/detail.jsp")));
    }

    @Test
    void linksStrutsActionMethodDirectActionForwardTargets() throws Exception {
        write("src/main/java/com/acme/UserAction.java", """
            package com.acme;
            class UserAction extends org.apache.struts.action.Action {
              org.apache.struts.action.ActionForward detail() {
                return new org.apache.struts.action.ActionForward("/user/detail.jsp");
              }
              org.apache.struts.action.ActionForward next() {
                return new org.apache.struts.action.ActionForward("/user/next.do");
              }
            }
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "project", tempDir);
        ProjectAnalysisResult result = new CodeAtlasProjectAnalyzer().analyze(scope, "shop");

        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().source().kind() == SymbolKind.METHOD
            && fact.factKey().source().ownerQualifiedName().equals("com.acme.UserAction")
            && fact.factKey().source().memberName().equals("detail")
            && fact.factKey().target().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().ownerQualifiedName().equals("user/detail.jsp")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().source().kind() == SymbolKind.METHOD
            && fact.factKey().source().ownerQualifiedName().equals("com.acme.UserAction")
            && fact.factKey().source().memberName().equals("next")
            && fact.factKey().target().kind() == SymbolKind.ACTION_PATH
            && fact.factKey().target().ownerQualifiedName().equals("user/next")));
    }

    @Test
    void linksStrutsNamedActionForwardConstructorUsingPathArgument() throws Exception {
        write("src/main/java/com/acme/UserAction.java", """
            package com.acme;
            class UserAction extends org.apache.struts.action.Action {
              org.apache.struts.action.ActionForward detail() {
                return new org.apache.struts.action.ActionForward("success", "/user/detail.jsp", true);
              }
            }
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "project", tempDir);
        ProjectAnalysisResult result = new CodeAtlasProjectAnalyzer().analyze(scope, "shop");

        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().source().kind() == SymbolKind.METHOD
            && fact.factKey().source().ownerQualifiedName().equals("com.acme.UserAction")
            && fact.factKey().source().memberName().equals("detail")
            && fact.factKey().target().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().ownerQualifiedName().equals("user/detail.jsp")
            && fact.factKey().qualifier().equals("new ActionForward:/user/detail.jsp")));
        assertTrue(result.facts().stream().noneMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().source().kind() == SymbolKind.METHOD
            && fact.factKey().source().ownerQualifiedName().equals("com.acme.UserAction")
            && fact.factKey().source().memberName().equals("detail")
            && fact.factKey().target().kind() == SymbolKind.ACTION_PATH
            && fact.factKey().target().ownerQualifiedName().equals("success")));
    }

    @Test
    void linksStrutsActionMethodRedirectTargets() throws Exception {
        write("src/main/java/com/acme/UserAction.java", """
            package com.acme;
            class UserAction extends org.apache.struts.action.Action {
              public org.apache.struts.action.ActionForward execute(
                  org.apache.struts.action.ActionMapping mapping,
                  org.apache.struts.action.ActionForm form,
                  javax.servlet.http.HttpServletRequest request,
                  javax.servlet.http.HttpServletResponse response) throws Exception {
                response.sendRedirect("/login.do");
                return new org.apache.struts.action.ActionRedirect("/user/detail.jsp");
              }
            }
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "project", tempDir);
        ProjectAnalysisResult result = new CodeAtlasProjectAnalyzer().analyze(scope, "shop");

        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().source().kind() == SymbolKind.METHOD
            && fact.factKey().source().ownerQualifiedName().equals("com.acme.UserAction")
            && fact.factKey().source().memberName().equals("execute")
            && fact.factKey().target().kind() == SymbolKind.ACTION_PATH
            && fact.factKey().target().ownerQualifiedName().equals("login")
            && fact.factKey().qualifier().equals("sendRedirect:/login.do")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().source().kind() == SymbolKind.METHOD
            && fact.factKey().source().ownerQualifiedName().equals("com.acme.UserAction")
            && fact.factKey().source().memberName().equals("execute")
            && fact.factKey().target().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().ownerQualifiedName().equals("user/detail.jsp")
            && fact.factKey().qualifier().equals("new ActionRedirect:/user/detail.jsp")));
    }

    @Test
    void analyzesSeasar2DiconSampleAsDiscoveryFacts() throws Exception {
        Path sampleRoot = Path.of("..", "samples", "seasar2-dicon").toAbsolutePath().normalize();
        assertTrue(Files.exists(sampleRoot.resolve("src/main/resources/app.dicon")));

        AnalyzerScope scope = new AnalyzerScope("seasar-sample", "_root", "snapshot-1", "run-1", "project", sampleRoot);
        ProjectAnalysisResult result = new CodeAtlasProjectAnalyzer().analyze(scope, "seasar-sample");

        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.BINDS_TO
            && fact.factKey().qualifier().equals("seasar-component")
            && fact.factKey().source().localId().equals("seasar:userService")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.INJECTS
            && fact.factKey().qualifier().equals("seasar-property:userDao")
            && fact.factKey().target().localId().equals("seasar:userDao")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.USES_CONFIG
            && fact.factKey().qualifier().equals("seasar-aspect:traceInterceptor")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.USES_CONFIG
            && fact.factKey().qualifier().equals("seasar-include:dao.dicon")));
        assertTrue(result.facts().stream()
            .filter(fact -> fact.factKey().qualifier().startsWith("seasar-"))
            .allMatch(fact -> fact.confidence() == org.sainm.codeatlas.graph.model.Confidence.POSSIBLE));
    }

    @Test
    void analyzesSpringMvcSampleEntrypointServiceRepositoryMapperSqlChain() throws Exception {
        Path sampleRoot = Path.of("..", "samples", "spring-mvc").toAbsolutePath().normalize();
        assertTrue(Files.exists(sampleRoot.resolve("src/main/java/com/acme/spring/web/UserController.java")));

        AnalyzerScope scope = new AnalyzerScope("spring-sample", "_root", "snapshot-1", "run-1", "project", sampleRoot);
        ProjectAnalysisResult result = new CodeAtlasProjectAnalyzer().analyze(scope, "spring-sample");

        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.API_ENDPOINT
            && node.symbolId().ownerQualifiedName().equals("users/{id}")
            && node.symbolId().localId().equals("GET")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.API_ENDPOINT
            && node.symbolId().ownerQualifiedName().equals("users/{id}/rename")
            && node.symbolId().localId().equals("POST")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.API_ENDPOINT
            && node.symbolId().localId().equals("SCHEDULED")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.API_ENDPOINT
            && node.symbolId().localId().equals("ASYNC")));

        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.ROUTES_TO
            && fact.factKey().source().kind() == SymbolKind.API_ENDPOINT
            && fact.factKey().source().ownerQualifiedName().equals("users/{id}")
            && fact.factKey().target().kind() == SymbolKind.METHOD
            && fact.factKey().target().ownerQualifiedName().equals("com.acme.spring.web.UserController")
            && fact.factKey().target().memberName().equals("find")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.INJECTS
            && fact.factKey().source().ownerQualifiedName().equals("com.acme.spring.web.UserController")
            && fact.factKey().target().ownerQualifiedName().equals("com.acme.spring.service.UserService")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.INJECTS
            && fact.factKey().source().ownerQualifiedName().equals("com.acme.spring.service.UserService")
            && fact.factKey().target().ownerQualifiedName().equals("com.acme.spring.repository.UserRepository")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.CALLS
            && fact.factKey().source().ownerQualifiedName().equals("com.acme.spring.web.UserController")
            && fact.factKey().source().memberName().equals("find")
            && fact.factKey().target().ownerQualifiedName().equals("com.acme.spring.service.UserService")
            && fact.factKey().target().memberName().equals("find")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.CALLS
            && fact.factKey().source().ownerQualifiedName().equals("com.acme.spring.repository.UserRepository")
            && fact.factKey().source().memberName().equals("findById")
            && fact.factKey().target().ownerQualifiedName().equals("com.acme.spring.mapper.UserMapper")
            && fact.factKey().target().memberName().equals("findById")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.BINDS_TO
            && fact.factKey().source().ownerQualifiedName().equals("com.acme.spring.mapper.UserMapper")
            && fact.factKey().source().memberName().equals("findById")
            && fact.factKey().target().kind() == SymbolKind.SQL_STATEMENT));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.READS_TABLE
            && fact.factKey().target().kind() == SymbolKind.DB_TABLE
            && fact.factKey().target().ownerQualifiedName().equals("users")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.WRITES_TABLE
            && fact.factKey().target().kind() == SymbolKind.DB_TABLE
            && fact.factKey().target().ownerQualifiedName().equals("users")));
    }

    @Test
    void analyzesSameStrutsConfigForMultipleModulePrefixes() throws Exception {
        write("src/main/webapp/WEB-INF/web.xml", """
            <web-app>
              <servlet>
                <servlet-name>action</servlet-name>
                <servlet-class>org.apache.struts.action.ActionServlet</servlet-class>
                <init-param>
                  <param-name>config/admin</param-name>
                  <param-value>/WEB-INF/struts-config-shared.xml</param-value>
                </init-param>
                <init-param>
                  <param-name>config/ops</param-name>
                  <param-value>/WEB-INF/struts-config-shared.xml</param-value>
                </init-param>
              </servlet>
            </web-app>
            """);
        write("src/main/webapp/WEB-INF/struts-config-shared.xml", """
            <struts-config>
              <action-mappings>
                <action path="/save" type="com.acme.SharedSaveAction"/>
              </action-mappings>
            </struts-config>
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "project", tempDir);
        ProjectAnalysisResult result = new CodeAtlasProjectAnalyzer().analyze(scope, "shop");

        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.ACTION_PATH
            && node.symbolId().ownerQualifiedName().equals("admin/save")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.ACTION_PATH
            && node.symbolId().ownerQualifiedName().equals("ops/save")));
    }

    @Test
    void linksStrutsDispatchActionPathToCandidateActionMethods() throws Exception {
        write("src/main/java/com/acme/UserDispatchAction.java", """
            package com.acme;
            class UserDispatchAction extends org.apache.struts.actions.DispatchAction {
              public org.apache.struts.action.ActionForward save(
                  org.apache.struts.action.ActionMapping mapping,
                  org.apache.struts.action.ActionForm form,
                  javax.servlet.http.HttpServletRequest request,
                  javax.servlet.http.HttpServletResponse response) {
                return null;
              }
              public org.apache.struts.action.ActionForward delete(
                  org.apache.struts.action.ActionMapping mapping,
                  org.apache.struts.action.ActionForm form,
                  javax.servlet.http.HttpServletRequest request,
                  javax.servlet.http.HttpServletResponse response) {
                return null;
              }
              void helper() {}
            }
            """);
        write("src/main/webapp/WEB-INF/struts-config.xml", """
            <struts-config>
              <action-mappings>
                <action path="/user/dispatch" type="com.acme.UserDispatchAction" parameter="method"/>
              </action-mappings>
            </struts-config>
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "project", tempDir);
        ProjectAnalysisResult result = new CodeAtlasProjectAnalyzer().analyze(scope, "shop");

        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.ROUTES_TO
            && fact.factKey().source().kind() == SymbolKind.ACTION_PATH
            && fact.factKey().source().ownerQualifiedName().equals("user/dispatch")
            && fact.factKey().target().kind() == SymbolKind.METHOD
            && fact.factKey().target().memberName().equals("save")
            && fact.factKey().qualifier().equals("dispatch-method-candidate:method:save")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.ROUTES_TO
            && fact.factKey().target().kind() == SymbolKind.METHOD
            && fact.factKey().target().memberName().equals("delete")
            && fact.confidence().name().equals("LIKELY")));
        assertTrue(result.facts().stream().noneMatch(fact -> fact.factKey().relationType() == RelationType.ROUTES_TO
            && fact.factKey().target().kind() == SymbolKind.METHOD
            && fact.factKey().target().memberName().equals("helper")));
    }

    @Test
    void linksLookupDispatchActionResourceKeysToActionMethods() throws Exception {
        write("src/main/java/com/acme/UserLookupAction.java", """
            package com.acme;
            import java.util.HashMap;
            import java.util.Map;
            class UserLookupAction extends org.apache.struts.actions.LookupDispatchAction {
              protected Map getKeyMethodMap() {
                Map methods = new HashMap();
                methods.put("button.save", "save");
                methods.put("button.delete", "delete");
                return methods;
              }
              public org.apache.struts.action.ActionForward save(
                  org.apache.struts.action.ActionMapping mapping,
                  org.apache.struts.action.ActionForm form,
                  javax.servlet.http.HttpServletRequest request,
                  javax.servlet.http.HttpServletResponse response) {
                return null;
              }
              public org.apache.struts.action.ActionForward delete(
                  org.apache.struts.action.ActionMapping mapping,
                  org.apache.struts.action.ActionForm form,
                  javax.servlet.http.HttpServletRequest request,
                  javax.servlet.http.HttpServletResponse response) {
                return null;
              }
            }
            """);
        write("src/main/webapp/WEB-INF/struts-config.xml", """
            <struts-config>
              <action-mappings>
                <action path="/user/lookup" type="com.acme.UserLookupAction" parameter="method"/>
              </action-mappings>
            </struts-config>
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "project", tempDir);
        ProjectAnalysisResult result = new CodeAtlasProjectAnalyzer().analyze(scope, "shop");

        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.ROUTES_TO
            && fact.factKey().source().kind() == SymbolKind.ACTION_PATH
            && fact.factKey().source().ownerQualifiedName().equals("user/lookup")
            && fact.factKey().target().kind() == SymbolKind.METHOD
            && fact.factKey().target().memberName().equals("save")
            && fact.factKey().qualifier().equals("lookup-dispatch-method:button.save:save")
            && fact.confidence().name().equals("LIKELY")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.ROUTES_TO
            && fact.factKey().target().kind() == SymbolKind.METHOD
            && fact.factKey().target().memberName().equals("delete")
            && fact.factKey().qualifier().equals("lookup-dispatch-method:button.delete:delete")));
    }

    @Test
    void linksIndirectStrutsActionSubclassToInheritedExecuteMethod() throws Exception {
        write("src/main/java/com/acme/BaseAction.java", """
            package com.acme;
            abstract class BaseAction extends org.apache.struts.action.Action {
              public org.apache.struts.action.ActionForward execute(
                  org.apache.struts.action.ActionMapping mapping,
                  org.apache.struts.action.ActionForm form,
                  javax.servlet.http.HttpServletRequest request,
                  javax.servlet.http.HttpServletResponse response) {
                return null;
              }
            }

            class UserAction extends BaseAction {
            }
            """);
        write("src/main/webapp/WEB-INF/struts-config.xml", """
            <struts-config>
              <action-mappings>
                <action path="/user/inherited" type="com.acme.UserAction"/>
              </action-mappings>
            </struts-config>
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "project", tempDir);
        ProjectAnalysisResult result = new CodeAtlasProjectAnalyzer().analyze(scope, "shop");

        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.ROUTES_TO
            && fact.factKey().source().kind() == SymbolKind.ACTION_PATH
            && fact.factKey().source().ownerQualifiedName().equals("user/inherited")
            && fact.factKey().target().kind() == SymbolKind.METHOD
            && fact.factKey().target().ownerQualifiedName().equals("com.acme.BaseAction")
            && fact.factKey().target().memberName().equals("execute")
            && fact.factKey().qualifier().equals("struts-action-execute")));
    }

    @Test
    void linksIndirectLookupDispatchActionUsingInheritedKeyMethodMap() throws Exception {
        write("src/main/java/com/acme/BaseLookupAction.java", """
            package com.acme;
            import java.util.HashMap;
            import java.util.Map;
            abstract class BaseLookupAction extends org.apache.struts.actions.LookupDispatchAction {
              protected Map getKeyMethodMap() {
                Map methods = new HashMap();
                methods.put("button.save", "save");
                methods.put("button.delete", "delete");
                return methods;
              }
            }

            class UserLookupAction extends BaseLookupAction {
              public org.apache.struts.action.ActionForward save(
                  org.apache.struts.action.ActionMapping mapping,
                  org.apache.struts.action.ActionForm form,
                  javax.servlet.http.HttpServletRequest request,
                  javax.servlet.http.HttpServletResponse response) {
                return null;
              }
              public org.apache.struts.action.ActionForward delete(
                  org.apache.struts.action.ActionMapping mapping,
                  org.apache.struts.action.ActionForm form,
                  javax.servlet.http.HttpServletRequest request,
                  javax.servlet.http.HttpServletResponse response) {
                return null;
              }
            }
            """);
        write("src/main/webapp/WEB-INF/struts-config.xml", """
            <struts-config>
              <action-mappings>
                <action path="/user/lookup-inherited" type="com.acme.UserLookupAction" parameter="method"/>
              </action-mappings>
            </struts-config>
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "project", tempDir);
        ProjectAnalysisResult result = new CodeAtlasProjectAnalyzer().analyze(scope, "shop");

        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.ROUTES_TO
            && fact.factKey().source().kind() == SymbolKind.ACTION_PATH
            && fact.factKey().source().ownerQualifiedName().equals("user/lookup-inherited")
            && fact.factKey().target().kind() == SymbolKind.METHOD
            && fact.factKey().target().ownerQualifiedName().equals("com.acme.UserLookupAction")
            && fact.factKey().target().memberName().equals("save")
            && fact.factKey().qualifier().equals("lookup-dispatch-method:button.save:save")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.ROUTES_TO
            && fact.factKey().source().ownerQualifiedName().equals("user/lookup-inherited")
            && fact.factKey().target().kind() == SymbolKind.METHOD
            && fact.factKey().target().ownerQualifiedName().equals("com.acme.UserLookupAction")
            && fact.factKey().target().memberName().equals("delete")
            && fact.factKey().qualifier().equals("lookup-dispatch-method:button.delete:delete")));
    }

    private void write(String relativePath, String content) throws Exception {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private void writeBusinessJar(String relativePath) throws Exception {
        Path source = tempDir.resolve("business-jar-src/com/vendor/logic/PricingLogic.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package com.vendor.logic;
            public class PricingLogic {
              public void apply(String userId) {
                PricingAudit.record(userId);
              }
            }
            class PricingAudit {
              static void record(String userId) {
              }
            }
            """);
        Path classes = tempDir.resolve("business-jar-classes");
        Files.createDirectories(classes);
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertTrue(compiler != null, "JDK compiler is required for classfile fixture");
        int exitCode = compiler.run(null, null, null, "-d", classes.toString(), source.toString());
        assertTrue(exitCode == 0, "business jar fixture compilation failed");

        Path jar = tempDir.resolve(relativePath);
        Files.createDirectories(jar.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            Path classFile = classes.resolve("com/vendor/logic/PricingLogic.class");
            output.putNextEntry(new JarEntry("com/vendor/logic/PricingLogic.class"));
            Files.copy(classFile, output);
            output.closeEntry();
            Path auditClassFile = classes.resolve("com/vendor/logic/PricingAudit.class");
            output.putNextEntry(new JarEntry("com/vendor/logic/PricingAudit.class"));
            Files.copy(auditClassFile, output);
            output.closeEntry();
        }
    }

    private void writeStrutsActionJar(String relativePath) throws Exception {
        Path actionSource = tempDir.resolve("struts-action-jar-src/com/vendor/web/JarSaveAction.java");
        Files.createDirectories(actionSource.getParent());
        Files.writeString(actionSource, """
            package com.vendor.web;
            public class JarSaveAction extends org.apache.struts.action.Action {
              public org.apache.struts.action.ActionForward execute(
                  org.apache.struts.action.ActionMapping mapping,
                  org.apache.struts.action.ActionForm form,
                  javax.servlet.http.HttpServletRequest request,
                  javax.servlet.http.HttpServletResponse response) {
                return null;
              }
            }
            """);
        writeStubSource("struts-action-jar-src/org/apache/struts/action/Action.java", """
            package org.apache.struts.action;
            public class Action {
            }
            """);
        writeStubSource("struts-action-jar-src/org/apache/struts/action/ActionForward.java", """
            package org.apache.struts.action;
            public class ActionForward {
            }
            """);
        writeStubSource("struts-action-jar-src/org/apache/struts/action/ActionForm.java", """
            package org.apache.struts.action;
            public class ActionForm {
            }
            """);
        writeStubSource("struts-action-jar-src/org/apache/struts/action/ActionMapping.java", """
            package org.apache.struts.action;
            public class ActionMapping {
            }
            """);
        writeStubSource("struts-action-jar-src/javax/servlet/http/HttpServletRequest.java", """
            package javax.servlet.http;
            public interface HttpServletRequest {
            }
            """);
        writeStubSource("struts-action-jar-src/javax/servlet/http/HttpServletResponse.java", """
            package javax.servlet.http;
            public interface HttpServletResponse {
            }
            """);

        Path classes = tempDir.resolve("struts-action-jar-classes");
        Files.createDirectories(classes);
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertTrue(compiler != null, "JDK compiler is required for classfile fixture");
        int exitCode = compiler.run(
            null,
            null,
            null,
            "-d",
            classes.toString(),
            actionSource.toString(),
            tempDir.resolve("struts-action-jar-src/org/apache/struts/action/Action.java").toString(),
            tempDir.resolve("struts-action-jar-src/org/apache/struts/action/ActionForward.java").toString(),
            tempDir.resolve("struts-action-jar-src/org/apache/struts/action/ActionForm.java").toString(),
            tempDir.resolve("struts-action-jar-src/org/apache/struts/action/ActionMapping.java").toString(),
            tempDir.resolve("struts-action-jar-src/javax/servlet/http/HttpServletRequest.java").toString(),
            tempDir.resolve("struts-action-jar-src/javax/servlet/http/HttpServletResponse.java").toString()
        );
        assertTrue(exitCode == 0, "struts action jar fixture compilation failed");

        Path jar = tempDir.resolve(relativePath);
        Files.createDirectories(jar.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            addClass(output, classes, "com/vendor/web/JarSaveAction.class");
            addClass(output, classes, "org/apache/struts/action/Action.class");
            addClass(output, classes, "org/apache/struts/action/ActionForward.class");
            addClass(output, classes, "org/apache/struts/action/ActionForm.class");
            addClass(output, classes, "org/apache/struts/action/ActionMapping.class");
            addClass(output, classes, "javax/servlet/http/HttpServletRequest.class");
            addClass(output, classes, "javax/servlet/http/HttpServletResponse.class");
        }
    }

    private void writeStrutsDispatchActionJar(String relativePath) throws Exception {
        Path actionSource = tempDir.resolve("struts-dispatch-action-jar-src/com/vendor/web/JarDispatchAction.java");
        Files.createDirectories(actionSource.getParent());
        Files.writeString(actionSource, """
            package com.vendor.web;
            public class JarDispatchAction extends org.apache.struts.actions.DispatchAction {
              public org.apache.struts.action.ActionForward save(
                  org.apache.struts.action.ActionMapping mapping,
                  org.apache.struts.action.ActionForm form,
                  javax.servlet.http.HttpServletRequest request,
                  javax.servlet.http.HttpServletResponse response) {
                return null;
              }
              public org.apache.struts.action.ActionForward delete(
                  org.apache.struts.action.ActionMapping mapping,
                  org.apache.struts.action.ActionForm form,
                  javax.servlet.http.HttpServletRequest request,
                  javax.servlet.http.HttpServletResponse response) {
                return null;
              }
              void helper() {
              }
            }
            """);
        writeDispatchStubSources("struts-dispatch-action-jar-src");

        Path classes = tempDir.resolve("struts-dispatch-action-jar-classes");
        Files.createDirectories(classes);
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertTrue(compiler != null, "JDK compiler is required for classfile fixture");
        int exitCode = compiler.run(
            null,
            null,
            null,
            "-d",
            classes.toString(),
            actionSource.toString(),
            tempDir.resolve("struts-dispatch-action-jar-src/org/apache/struts/action/Action.java").toString(),
            tempDir.resolve("struts-dispatch-action-jar-src/org/apache/struts/actions/DispatchAction.java").toString(),
            tempDir.resolve("struts-dispatch-action-jar-src/org/apache/struts/action/ActionForward.java").toString(),
            tempDir.resolve("struts-dispatch-action-jar-src/org/apache/struts/action/ActionForm.java").toString(),
            tempDir.resolve("struts-dispatch-action-jar-src/org/apache/struts/action/ActionMapping.java").toString(),
            tempDir.resolve("struts-dispatch-action-jar-src/javax/servlet/http/HttpServletRequest.java").toString(),
            tempDir.resolve("struts-dispatch-action-jar-src/javax/servlet/http/HttpServletResponse.java").toString()
        );
        assertTrue(exitCode == 0, "struts dispatch action jar fixture compilation failed");

        Path jar = tempDir.resolve(relativePath);
        Files.createDirectories(jar.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            addClass(output, classes, "com/vendor/web/JarDispatchAction.class");
            addClass(output, classes, "org/apache/struts/action/Action.class");
            addClass(output, classes, "org/apache/struts/actions/DispatchAction.class");
            addClass(output, classes, "org/apache/struts/action/ActionForward.class");
            addClass(output, classes, "org/apache/struts/action/ActionForm.class");
            addClass(output, classes, "org/apache/struts/action/ActionMapping.class");
            addClass(output, classes, "javax/servlet/http/HttpServletRequest.class");
            addClass(output, classes, "javax/servlet/http/HttpServletResponse.class");
        }
    }

    private void writeStrutsActionFormJar(String relativePath) throws Exception {
        Path formSource = tempDir.resolve("struts-form-jar-src/com/vendor/web/JarUserForm.java");
        Files.createDirectories(formSource.getParent());
        Files.writeString(formSource, """
            package com.vendor.web;
            public class JarUserForm extends org.apache.struts.action.ActionForm {
              private String userId;
              private static String ignored;
            }
            """);
        writeStubSource("struts-form-jar-src/org/apache/struts/action/ActionForm.java", """
            package org.apache.struts.action;
            public class ActionForm {
            }
            """);

        Path classes = tempDir.resolve("struts-form-jar-classes");
        Files.createDirectories(classes);
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertTrue(compiler != null, "JDK compiler is required for classfile fixture");
        int exitCode = compiler.run(
            null,
            null,
            null,
            "-d",
            classes.toString(),
            formSource.toString(),
            tempDir.resolve("struts-form-jar-src/org/apache/struts/action/ActionForm.java").toString()
        );
        assertTrue(exitCode == 0, "struts action form jar fixture compilation failed");

        Path jar = tempDir.resolve(relativePath);
        Files.createDirectories(jar.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            addClass(output, classes, "com/vendor/web/JarUserForm.class");
            addClass(output, classes, "org/apache/struts/action/ActionForm.class");
        }
    }

    private void writeDispatchStubSources(String sourceRoot) throws Exception {
        writeStubSource(sourceRoot + "/org/apache/struts/action/Action.java", """
            package org.apache.struts.action;
            public class Action {
            }
            """);
        writeStubSource(sourceRoot + "/org/apache/struts/actions/DispatchAction.java", """
            package org.apache.struts.actions;
            public class DispatchAction extends org.apache.struts.action.Action {
            }
            """);
        writeStubSource(sourceRoot + "/org/apache/struts/action/ActionForward.java", """
            package org.apache.struts.action;
            public class ActionForward {
            }
            """);
        writeStubSource(sourceRoot + "/org/apache/struts/action/ActionForm.java", """
            package org.apache.struts.action;
            public class ActionForm {
            }
            """);
        writeStubSource(sourceRoot + "/org/apache/struts/action/ActionMapping.java", """
            package org.apache.struts.action;
            public class ActionMapping {
            }
            """);
        writeStubSource(sourceRoot + "/javax/servlet/http/HttpServletRequest.java", """
            package javax.servlet.http;
            public interface HttpServletRequest {
            }
            """);
        writeStubSource(sourceRoot + "/javax/servlet/http/HttpServletResponse.java", """
            package javax.servlet.http;
            public interface HttpServletResponse {
            }
            """);
    }

    private void writeStubSource(String relativePath, String content) throws Exception {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static void addClass(JarOutputStream output, Path classes, String entryName) throws Exception {
        output.putNextEntry(new JarEntry(entryName));
        Files.copy(classes.resolve(entryName), output);
        output.closeEntry();
    }
}
