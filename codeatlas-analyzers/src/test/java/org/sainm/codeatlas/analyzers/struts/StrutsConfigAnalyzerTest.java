package org.sainm.codeatlas.analyzers.struts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SymbolKind;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StrutsConfigAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsActionFormAndForwardLinks() throws Exception {
        Path config = tempDir.resolve("WEB-INF/struts-config.xml");
        Files.createDirectories(config.getParent());
        Files.writeString(config, """
            <?xml version="1.0" encoding="UTF-8"?>
            <struts-config>
              <controller processorClass="com.acme.CustomRequestProcessor" multipartClass="com.acme.UploadRequestHandler" inputForward="true" maxFileSize="10M"/>
              <message-resources parameter="com.acme.ApplicationResources" key="app" factory="com.acme.MessageFactory" null="false"/>
              <global-forwards>
                <forward name="login" path="/login.jsp"/>
              </global-forwards>
              <global-exceptions>
                <exception key="error.auth" type="com.acme.AuthException" path="/login.jsp" handler="com.acme.AuthExceptionHandler"/>
              </global-exceptions>
              <form-beans>
                <form-bean name="userForm" type="com.acme.UserForm"/>
              </form-beans>
              <action-mappings type="com.acme.CustomActionMapping">
                <action path="/user/save" type="com.acme.UserAction" name="userForm" scope="request" input="/user/edit.jsp" parameter="method" className="com.acme.UserSaveActionMapping">
                  <forward name="success" path="/user/detail.jsp"/>
                  <exception key="error.save" type="com.acme.SaveException" path="/user/edit.jsp"/>
                </action>
              </action-mappings>
              <plug-in className="org.apache.struts.tiles.TilesPlugin">
                <set-property property="definitions-config" value="/WEB-INF/tiles-defs.xml"/>
                <set-property property="moduleAware" value="true"/>
              </plug-in>
            </struts-config>
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "WEB-INF/struts-config.xml", tempDir);
        StrutsConfigAnalysisResult result = new StrutsConfigAnalyzer().analyze(scope, "shop", "src/main/webapp", config);

        assertEquals(1, result.formBeans().size());
        assertEquals(1, result.actionMappings().size());
        assertEquals("method", result.actionMappings().getFirst().parameter());
        assertEquals("com.acme.UserSaveActionMapping", result.actionMappings().getFirst().className());
        assertEquals(2, result.forwards().size());
        assertTrue(result.forwards().stream().anyMatch(forward -> forward.actionPath().equals("_global")
            && forward.name().equals("login")));
        assertTrue(result.forwards().stream().anyMatch(forward -> forward.actionPath().equals("/user/save")
            && forward.name().equals("success")));
        assertEquals(1, result.plugins().size());
        assertEquals("org.apache.struts.tiles.TilesPlugin", result.plugins().getFirst().className());
        assertEquals("/WEB-INF/tiles-defs.xml", result.plugins().getFirst().properties().get("definitions-config"));
        assertEquals(1, result.controllers().size());
        assertEquals("com.acme.CustomRequestProcessor", result.controllers().getFirst().attributes().get("processorClass"));
        assertEquals("true", result.controllers().getFirst().attributes().get("inputForward"));
        assertEquals(1, result.messageResources().size());
        assertEquals("com.acme.ApplicationResources", result.messageResources().getFirst().parameter());
        assertEquals("com.acme.MessageFactory", result.messageResources().getFirst().factory());
        assertEquals(2, result.exceptions().size());
        assertTrue(result.exceptions().stream().anyMatch(exception -> exception.type().equals("com.acme.AuthException")
            && exception.handler().equals("com.acme.AuthExceptionHandler")));
        assertTrue(result.exceptions().stream().anyMatch(exception -> exception.scope().equals("/user/save")
            && exception.type().equals("com.acme.SaveException")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.ACTION_PATH
            && node.symbolId().ownerQualifiedName().equals("user/save")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.CLASS
            && node.symbolId().ownerQualifiedName().equals("com.acme.UserAction")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.CLASS
            && node.symbolId().ownerQualifiedName().equals("com.acme.CustomRequestProcessor")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.CLASS
            && node.symbolId().ownerQualifiedName().equals("com.acme.UploadRequestHandler")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.CLASS
            && node.symbolId().ownerQualifiedName().equals("org.apache.struts.tiles.TilesPlugin")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.CLASS
            && node.symbolId().ownerQualifiedName().equals("com.acme.AuthException")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.CLASS
            && node.symbolId().ownerQualifiedName().equals("com.acme.MessageFactory")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.CLASS
            && node.symbolId().ownerQualifiedName().equals("com.acme.CustomActionMapping")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.CLASS
            && node.symbolId().ownerQualifiedName().equals("com.acme.UserSaveActionMapping")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.CONFIG_KEY
            && node.symbolId().localId().contains("definitions-config")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.ROUTES_TO));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.BINDS_TO));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.USES_CONFIG
            && fact.factKey().target().ownerQualifiedName().equals("org.apache.struts.tiles.TilesPlugin")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.USES_CONFIG
            && fact.factKey().target().ownerQualifiedName().equals("com.acme.CustomRequestProcessor")
            && fact.factKey().qualifier().equals("controller-processorClass")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.USES_CONFIG
            && fact.factKey().qualifier().contains("controller-attribute:inputForward")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.USES_CONFIG
            && fact.factKey().qualifier().contains("plugin-property:definitions-config")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.USES_CONFIG
            && fact.factKey().qualifier().equals("message-resources:com.acme.ApplicationResources")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.USES_CONFIG
            && fact.factKey().target().ownerQualifiedName().equals("com.acme.CustomActionMapping")
            && fact.factKey().qualifier().equals("action-mappings-type")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.USES_CONFIG
            && fact.factKey().source().kind() == SymbolKind.ACTION_PATH
            && fact.factKey().source().ownerQualifiedName().equals("user/save")
            && fact.factKey().target().ownerQualifiedName().equals("com.acme.UserSaveActionMapping")
            && fact.factKey().qualifier().equals("action-mapping-className")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().qualifier().equals("global-forward:login")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.CONFIG_KEY
            && node.symbolId().ownerQualifiedName().equals("struts-forward")
            && node.symbolId().localId().equals("success")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.USES_CONFIG
            && fact.factKey().source().kind() == SymbolKind.ACTION_PATH
            && fact.factKey().source().ownerQualifiedName().equals("user/save")
            && fact.factKey().target().kind() == SymbolKind.CONFIG_KEY
            && fact.factKey().target().ownerQualifiedName().equals("struts-forward")
            && fact.factKey().target().localId().equals("success")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().source().kind() == SymbolKind.CONFIG_KEY
            && fact.factKey().source().ownerQualifiedName().equals("struts-forward")
            && fact.factKey().source().localId().equals("success")
            && fact.factKey().target().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().ownerQualifiedName().equals("user/detail.jsp")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().qualifier().equals("exception-path:error.auth")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.READS_PARAM
            && fact.factKey().target().kind() == SymbolKind.REQUEST_PARAMETER
            && fact.factKey().target().ownerQualifiedName().equals("method")
            && fact.factKey().qualifier().equals("dispatch-parameter:method")));
    }

    @Test
    void toleratesLegacyStrutsDoctypeWithoutLoadingExternalDtd() throws Exception {
        Path config = tempDir.resolve("WEB-INF/struts-config.xml");
        Files.createDirectories(config.getParent());
        Files.writeString(config, """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE struts-config PUBLIC "-//Apache Software Foundation//DTD Struts Configuration 1.1//EN"
              "http://struts.apache.org/dtds/struts-config_1_1.dtd">
            <struts-config>
              <action-mappings>
                <action path="/legacy/run" type="com.acme.LegacyAction"/>
              </action-mappings>
            </struts-config>
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "WEB-INF/struts-config.xml", tempDir);
        StrutsConfigAnalysisResult result = new StrutsConfigAnalyzer().analyze(scope, "shop", "src/main/webapp", config);

        assertEquals(1, result.actionMappings().size());
        assertEquals("/legacy/run", result.actionMappings().getFirst().path());
    }

    @Test
    void extractsDynaActionFormPropertiesAsRequestParameterBindings() throws Exception {
        Path config = tempDir.resolve("WEB-INF/struts-config.xml");
        Files.createDirectories(config.getParent());
        Files.writeString(config, """
            <struts-config>
              <form-beans>
                <form-bean name="searchForm" type="org.apache.struts.action.DynaActionForm">
                  <form-property name="keyword" type="java.lang.String"/>
                  <form-property name="page" type="java.lang.Integer" initial="1"/>
                </form-bean>
              </form-beans>
              <action-mappings>
                <action path="/user/search" type="com.acme.SearchAction" name="searchForm"/>
              </action-mappings>
            </struts-config>
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "WEB-INF/struts-config.xml", tempDir);
        StrutsConfigAnalysisResult result = new StrutsConfigAnalyzer().analyze(scope, "shop", "src/main/webapp", config);

        assertEquals(1, result.formBeans().size());
        assertEquals(2, result.formBeans().getFirst().properties().size());
        assertEquals("page", result.formBeans().getFirst().properties().get(1).name());
        assertEquals("1", result.formBeans().getFirst().properties().get(1).initial());
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.CONFIG_KEY
            && node.symbolId().localId().contains("form-bean:searchForm:property:keyword")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.REQUEST_PARAMETER
            && node.symbolId().ownerQualifiedName().equals("keyword")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.BINDS_TO
            && fact.factKey().source().kind() == SymbolKind.REQUEST_PARAMETER
            && fact.factKey().source().ownerQualifiedName().equals("keyword")
            && fact.factKey().target().kind() == SymbolKind.CONFIG_KEY));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.DECLARES
            && fact.factKey().qualifier().equals("form-property:page")));
    }

    @Test
    void appliesModulePrefixToActionPathNodes() throws Exception {
        Path config = tempDir.resolve("WEB-INF/struts-config-admin.xml");
        Files.createDirectories(config.getParent());
        Files.writeString(config, """
            <struts-config>
              <global-forwards>
                <forward name="list" path="/user/list.do"/>
              </global-forwards>
              <global-exceptions>
                <exception key="error.next" type="com.acme.NextException" path="/next.do"/>
              </global-exceptions>
              <action-mappings>
                <action path="/save" type="com.acme.AdminSaveAction">
                  <forward name="next" path="/next.do"/>
                </action>
              </action-mappings>
            </struts-config>
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "WEB-INF/struts-config-admin.xml", tempDir);
        StrutsConfigAnalysisResult result = new StrutsConfigAnalyzer().analyze(scope, "shop", "src/main/webapp", config, "/admin");

        assertEquals("/save", result.actionMappings().getFirst().path());
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.ACTION_PATH
            && node.symbolId().ownerQualifiedName().equals("admin/save")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.ROUTES_TO
            && fact.factKey().source().ownerQualifiedName().equals("admin/save")
            && fact.factKey().target().ownerQualifiedName().equals("com.acme.AdminSaveAction")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().qualifier().equals("forward:next")
            && fact.factKey().target().kind() == SymbolKind.ACTION_PATH
            && fact.factKey().target().ownerQualifiedName().equals("admin/next")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().qualifier().equals("global-forward:list")
            && fact.factKey().target().kind() == SymbolKind.ACTION_PATH
            && fact.factKey().target().ownerQualifiedName().equals("admin/user/list")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().qualifier().equals("exception-path:error.next")
            && fact.factKey().target().kind() == SymbolKind.ACTION_PATH
            && fact.factKey().target().ownerQualifiedName().equals("admin/next")));
    }

    @Test
    void supportsForwardOnlyActionsWithoutActionClass() throws Exception {
        Path config = tempDir.resolve("WEB-INF/struts-config.xml");
        Files.createDirectories(config.getParent());
        Files.writeString(config, """
            <struts-config>
              <action-mappings>
                <action path="/user/edit" forward="/user/edit.jsp"/>
              </action-mappings>
            </struts-config>
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "WEB-INF/struts-config.xml", tempDir);
        StrutsConfigAnalysisResult result = new StrutsConfigAnalyzer().analyze(scope, "shop", "src/main/webapp", config);

        assertEquals(1, result.actionMappings().size());
        assertEquals("/user/edit.jsp", result.actionMappings().getFirst().forward());
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.ACTION_PATH
            && node.symbolId().ownerQualifiedName().equals("user/edit")));
        assertTrue(result.facts().stream().noneMatch(fact -> fact.factKey().relationType() == RelationType.ROUTES_TO));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().qualifier().equals("action-forward")
            && fact.factKey().target().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().ownerQualifiedName().equals("user/edit.jsp")));
    }

    @Test
    void mapsTilesDefinitionForwardsToConfigKeys() throws Exception {
        Path config = tempDir.resolve("WEB-INF/struts-config.xml");
        Files.createDirectories(config.getParent());
        Files.writeString(config, """
            <struts-config>
              <action-mappings>
                <action path="/user/save" type="com.acme.UserAction">
                  <forward name="success" path="legacy.user.detail"/>
                </action>
              </action-mappings>
            </struts-config>
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "WEB-INF/struts-config.xml", tempDir);
        StrutsConfigAnalysisResult result = new StrutsConfigAnalyzer().analyze(scope, "shop", "src/main/webapp", config);

        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.CONFIG_KEY
            && node.symbolId().localId().equals("tiles-definition:legacy.user.detail")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().qualifier().equals("forward:success")
            && fact.factKey().target().kind() == SymbolKind.CONFIG_KEY
            && fact.factKey().target().localId().equals("tiles-definition:legacy.user.detail")));
    }
}
