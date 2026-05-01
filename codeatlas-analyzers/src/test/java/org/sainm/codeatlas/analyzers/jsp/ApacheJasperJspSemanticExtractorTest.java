package org.sainm.codeatlas.analyzers.jsp;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApacheJasperJspSemanticExtractorTest {
    @TempDir
    Path tempDir;

    @Test
    void parsesStandardJspNodesWithApacheJasper() throws Exception {
        Path webRoot = tempDir.resolve("src/main/webapp");
        Path page = webRoot.resolve("user/view.jsp");
        Files.createDirectories(page.getParent());
        Files.writeString(webRoot.resolve("part.jsp"), "part");
        Files.writeString(page, """
            <%@ page pageEncoding="UTF-8" %>
            <%@ include file="/part.jsp" %>
            <jsp:include page="/part.jsp"/>
            <jsp:forward page="/done.jsp"/>
            <% String id = request.getParameter("id"); %>
            <%= id %>
            ${user.name}
            """);
        WebAppContext context = new WebAppContext(webRoot, null, "2.5", "2.1", "generic", List.of(), Map.of(), "UTF-8");

        JspSemanticAnalysis analysis = new ApacheJasperJspSemanticExtractor().extract(page, context);

        assertNotNull(analysis);
        assertTrue(analysis.parserSource() == JspSemanticParserSource.APACHE_JASPER);
        assertTrue(analysis.parserName().equals("apache-jasper"));
        assertNull(analysis.fallbackReason());
        assertTrue(analysis.directives().stream().anyMatch(directive -> directive.name().equals("page")));
        assertTrue(analysis.includes().contains("/part.jsp"));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("jsp:include")
            && "/part.jsp".equals(action.attributes().get("page"))));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("jsp:forward")
            && "/done.jsp".equals(action.attributes().get("page"))));
        assertTrue(analysis.expressions().stream().anyMatch(expression -> expression.kind().equals("SCRIPTLET")));
        assertTrue(analysis.expressions().stream().anyMatch(expression -> expression.kind().equals("EXPRESSION")));
        assertTrue(analysis.expressions().stream().anyMatch(expression -> expression.kind().equals("EL")));
    }
}
