package org.sainm.codeatlas.analyzers.jsp;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApacheJasperJspSemanticExtractorOutputTest {
    @TempDir
    Path tempDir;

    @Test
    void printsApacheJasperParseOutputForARealJsp() throws Exception {
        Path webRoot = tempDir.resolve("src/main/webapp");
        Path page = webRoot.resolve("user/view.jsp");
        Files.createDirectories(page.getParent());
        Files.writeString(webRoot.resolve("part.jsp"), "<section>part</section>");
        Files.writeString(page, """
            <%@ page pageEncoding="UTF-8" contentType="text/html; charset=UTF-8" %>
            <%@ include file="/part.jsp" %>
            <jsp:include page="/part.jsp">
              <jsp:param name="mode" value="preview"/>
            </jsp:include>
            <jsp:forward page="/done.jsp"/>
            <% String id = request.getParameter("id"); %>
            <%= id %>
            ${user.name}
            """);
        WebAppContext context = new WebAppContext(webRoot, null, "2.5", "2.1", "generic", List.of(), Map.of(), "UTF-8");

        JspSemanticAnalysis analysis = new ApacheJasperJspSemanticExtractor().extract(page, context);

        assertNotNull(analysis);
        System.out.println("JASPER_PARSE_OUTPUT");
        System.out.println("jspFile=" + page);
        System.out.println("encoding=" + analysis.encoding());
        System.out.println("directives=" + analysis.directives().size());
        for (JspDirective directive : analysis.directives()) {
            System.out.println("directive line=" + directive.line() + " name=" + directive.name() + " attrs=" + directive.attributes());
        }
        System.out.println("includes=" + analysis.includes());
        System.out.println("actions=" + analysis.actions().size());
        for (JspAction action : analysis.actions()) {
            System.out.println("action line=" + action.line() + " name=" + action.name() + " attrs=" + action.attributes());
        }
        System.out.println("expressions=" + analysis.expressions().size());
        for (JspExpressionFragment expression : analysis.expressions()) {
            System.out.println("expression line=" + expression.line() + " kind=" + expression.kind() + " text=" + expression.expression().trim());
        }
    }
}
