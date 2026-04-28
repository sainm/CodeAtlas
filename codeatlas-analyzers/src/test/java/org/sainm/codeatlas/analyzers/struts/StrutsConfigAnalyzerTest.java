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
              <form-beans>
                <form-bean name="userForm" type="com.acme.UserForm"/>
              </form-beans>
              <action-mappings>
                <action path="/user/save" type="com.acme.UserAction" name="userForm" scope="request" input="/user/edit.jsp">
                  <forward name="success" path="/user/detail.jsp"/>
                </action>
              </action-mappings>
            </struts-config>
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "WEB-INF/struts-config.xml", tempDir);
        StrutsConfigAnalysisResult result = new StrutsConfigAnalyzer().analyze(scope, "shop", "src/main/webapp", config);

        assertEquals(1, result.formBeans().size());
        assertEquals(1, result.actionMappings().size());
        assertEquals(1, result.forwards().size());
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.ACTION_PATH
            && node.symbolId().ownerQualifiedName().equals("user/save")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.CLASS
            && node.symbolId().ownerQualifiedName().equals("com.acme.UserAction")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.ROUTES_TO));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.BINDS_TO));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO));
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
