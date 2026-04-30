package org.sainm.codeatlas.analyzers.struts;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SymbolKind;

class StrutsPluginInitXmlAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void linksPluginXmlPropertiesToImportedConfigEntriesAndPossibleTables() throws Exception {
        Path config = tempDir.resolve("src/main/webapp/WEB-INF/struts-config.xml");
        Files.createDirectories(config.getParent());
        Files.writeString(config, """
            <struts-config>
              <plug-in className="com.acme.MasterDataPlugin">
                <set-property property="locations" value="/WEB-INF/master-data.xml,/WEB-INF/menu.xml"/>
              </plug-in>
            </struts-config>
            """);
        Path master = tempDir.resolve("src/main/webapp/WEB-INF/master-data.xml");
        Files.writeString(master, """
            <master-data>
              <code id="status.active" table="code_master"/>
              <role key="admin"/>
            </master-data>
            """);
        Path menu = tempDir.resolve("src/main/webapp/WEB-INF/menu.xml");
        Files.writeString(menu, """
            <menus>
              <menu key="user.edit"/>
            </menus>
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "src/main/webapp", tempDir);
        StrutsConfigAnalysisResult strutsResult = new StrutsConfigAnalyzer().analyze(scope, "shop", "src/main/webapp", config);
        StrutsPluginInitXmlAnalysisResult result = new StrutsPluginInitXmlAnalyzer().analyze(scope, "shop", "src/main/webapp", config, strutsResult);

        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.USES_CONFIG
            && fact.factKey().qualifier().equals("plugin-init-xml:locations=/WEB-INF/master-data.xml")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.USES_CONFIG
            && fact.factKey().qualifier().equals("plugin-init-xml:locations=/WEB-INF/menu.xml")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.DECLARES
            && fact.factKey().target().kind() == SymbolKind.CONFIG_KEY
            && fact.factKey().target().localId().equals("xml-entry:code:status.active")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.WRITES_TABLE
            && fact.factKey().target().kind() == SymbolKind.DB_TABLE
            && fact.factKey().target().ownerQualifiedName().equals("code_master")
            && fact.confidence().name().equals("POSSIBLE")));
    }
}

