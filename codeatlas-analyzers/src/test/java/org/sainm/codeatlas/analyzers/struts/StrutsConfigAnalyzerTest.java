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
              <form-beans>
                <form-bean name="userForm" type="com.acme.UserForm"/>
              </form-beans>
              <action-mappings>
                <action path="/user/save" type="com.acme.UserAction" name="userForm" scope="request" input="/user/edit.jsp" parameter="method">
                  <forward name="success" path="/user/detail.jsp"/>
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
        assertEquals(1, result.forwards().size());
        assertEquals(1, result.plugins().size());
        assertEquals("org.apache.struts.tiles.TilesPlugin", result.plugins().getFirst().className());
        assertEquals("/WEB-INF/tiles-defs.xml", result.plugins().getFirst().properties().get("definitions-config"));
        assertEquals(1, result.controllers().size());
        assertEquals("com.acme.CustomRequestProcessor", result.controllers().getFirst().attributes().get("processorClass"));
        assertEquals("true", result.controllers().getFirst().attributes().get("inputForward"));
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
}
