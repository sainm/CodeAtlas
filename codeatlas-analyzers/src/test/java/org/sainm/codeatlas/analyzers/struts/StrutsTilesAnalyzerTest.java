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

class StrutsTilesAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsTilesDefinitionsAndJspTargets() throws Exception {
        Path tiles = tempDir.resolve("WEB-INF/tiles-defs.xml");
        Files.createDirectories(tiles.getParent());
        Files.writeString(tiles, """
            <tiles-definitions>
              <definition name="base.layout" path="/WEB-INF/layouts/base.jsp">
                <put name="title" value="Users"/>
                <put name="body" value="/WEB-INF/jsp/user/body.jsp"/>
              </definition>
              <definition name="user.detail" extends="base.layout">
                <put name="body" value="/WEB-INF/jsp/user/detail.jsp"/>
              </definition>
            </tiles-definitions>
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "WEB-INF/tiles-defs.xml", tempDir);
        StrutsTilesAnalysisResult result = new StrutsTilesAnalyzer().analyze(scope, "shop", "src/main/webapp", tiles);

        assertEquals(2, result.definitions().size());
        assertTrue(result.definitions().stream().anyMatch(definition -> definition.name().equals("user.detail")
            && definition.extendsName().equals("base.layout")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.CONFIG_KEY
            && node.symbolId().localId().contains("tiles-definition:user.detail")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.JSP_PAGE
            && node.symbolId().ownerQualifiedName().endsWith("WEB-INF/jsp/user/detail.jsp")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.EXTENDS
            && fact.factKey().qualifier().equals("tiles-extends")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().qualifier().equals("tiles-put-value:body")));
    }
}
