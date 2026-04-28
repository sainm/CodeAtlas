package org.sainm.codeatlas.analyzers.jsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SymbolKind;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JspFormAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsStrutsHtmlFormInputsAndActionPath() throws Exception {
        Path jsp = tempDir.resolve("src/main/webapp/user/edit.jsp");
        Files.createDirectories(jsp.getParent());
        Files.writeString(jsp, """
            <%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
            <html:form action="/user/save.do" method="post">
              <html:text property="name"/>
              <html:hidden property="id"/>
            </html:form>
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "src/main/webapp/user/edit.jsp", tempDir);
        JspAnalysisResult result = new JspFormAnalyzer().analyze(scope, "shop", "src/main/webapp", jsp);

        assertEquals(1, result.forms().size());
        assertEquals(2, result.forms().getFirst().inputs().size());
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.JSP_PAGE));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.JSP_FORM));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.JSP_INPUT
            && node.symbolId().localId().contains("name")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.REQUEST_PARAMETER
            && node.symbolId().ownerQualifiedName().equals("name")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.ACTION_PATH
            && node.symbolId().ownerQualifiedName().equals("user/save")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.SUBMITS_TO));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.WRITES_PARAM
            && fact.factKey().qualifier().equals("name")));
    }
}
