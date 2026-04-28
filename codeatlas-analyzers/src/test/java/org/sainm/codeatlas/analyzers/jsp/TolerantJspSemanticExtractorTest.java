package org.sainm.codeatlas.analyzers.jsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TolerantJspSemanticExtractorTest {
    @Test
    void extractsDirectivesActionsExpressionsAndIncludes() {
        String jsp = """
            <%@ page pageEncoding="Shift_JIS" %>
            <%@ include file="/common/header.jsp" %>
            <jsp:include page="/part.jsp"/>
            <jsp:forward page="/done.jsp"/>
            <c:if test="${user != null}">${user.name}</c:if>
            <% String value = request.getParameter("id"); %>
            <%= value %>
            """;
        WebAppContext context = new WebAppContext(Path.of("."), null, "2.4", "unknown", "generic", List.of(), Map.of(), "UTF-8");

        JspSemanticAnalysis analysis = new TolerantJspSemanticExtractor().extract(jsp, context);

        assertEquals("Shift_JIS", analysis.encoding());
        assertEquals(List.of("/common/header.jsp"), analysis.includes());
        assertTrue(analysis.directives().stream().anyMatch(directive -> directive.name().equals("page")));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("jsp:include")));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("c:if")));
        assertTrue(analysis.expressions().stream().anyMatch(expression -> expression.kind().equals("EL")));
        assertTrue(analysis.expressions().stream().anyMatch(expression -> expression.kind().equals("SCRIPTLET")));
        assertTrue(analysis.expressions().stream().anyMatch(expression -> expression.kind().equals("EXPRESSION")));
    }
}
