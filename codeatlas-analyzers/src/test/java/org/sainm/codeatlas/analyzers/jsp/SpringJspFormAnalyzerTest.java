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

class SpringJspFormAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void emitsSpringFormPathAndOptionSourceFacts() throws Exception {
        Path jsp = tempDir.resolve("src/main/webapp/spring/user.jsp");
        Files.createDirectories(jsp.getParent());
        Files.writeString(jsp, """
            <%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
            <form:form action="/spring/user/save" method="post" modelAttribute="userForm">
              <form:input path="name"/>
              <form:hidden path="id"/>
              <form:select path="role">
                <form:options items="${roleOptions}" itemValue="code" itemLabel="label"/>
              </form:select>
            </form:form>
            """);
        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "src/main/webapp/spring/user.jsp", tempDir);

        JspAnalysisResult result = new JspFormAnalyzer().analyze(scope, "shop", "src/main/webapp", jsp);

        assertEquals(1, result.forms().size());
        assertEquals(3, result.forms().getFirst().inputs().size());
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.SUBMITS_TO
            && fact.factKey().target().kind() == SymbolKind.ACTION_PATH
            && fact.factKey().target().ownerQualifiedName().equals("spring/user/save")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.WRITES_PARAM
            && fact.factKey().target().kind() == SymbolKind.REQUEST_PARAMETER
            && fact.factKey().target().ownerQualifiedName().equals("name")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.WRITES_PARAM
            && fact.factKey().target().kind() == SymbolKind.REQUEST_PARAMETER
            && fact.factKey().target().ownerQualifiedName().equals("role")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.USES_CONFIG
            && fact.factKey().target().kind() == SymbolKind.CONFIG_KEY
            && fact.factKey().qualifier().equals("form:options:source=roleOptions")));
    }
}
