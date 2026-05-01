package org.sainm.codeatlas.analyzers.jsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JerichoJspSemanticExtractorTest {
    @Test
    void extractsLegacyJspTagsWithoutScriptAndStyleFalsePositives() {
        WebAppContext context = new WebAppContext(Path.of("."), null, "2.4", "unknown", "generic", List.of(), Map.of(), "UTF-8");

        JspSemanticAnalysis analysis = new JerichoJspSemanticExtractor().extract("""
            <%@ taglib prefix="html" uri="http://struts.apache.org/tags-html" %>
            <html:form action="/user/save.do">
              <html:text property="name"/>
              <logic:present name="currentUser">
                <bean:write name="currentUser" property="displayName"/>
              </logic:present>
            </html:form>
            <jsp:include page="/common/menu.jsp"/>
            <script>
              const fake = '<html:text property="fakeJs"/>';
            </script>
            <style>
              .fake { content: '<logic:redirect action="/fake.do"/>'; }
            </style>
            """, context);

        assertEquals(JspSemanticParserSource.JERICHO_WITH_TOKENIZER_MERGE, analysis.parserSource());
        assertEquals("jericho-html+tolerant-jsp-tokenizer", analysis.parserName());
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("html:form")
            && "/user/save.do".equals(action.attributes().get("action"))));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("html:text")
            && "name".equals(action.attributes().get("property"))));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("logic:present")
            && "currentUser".equals(action.attributes().get("name"))));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("bean:write")
            && "displayName".equals(action.attributes().get("property"))));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("jsp:include")
            && "/common/menu.jsp".equals(action.attributes().get("page"))));
        assertTrue(analysis.actions().stream().noneMatch(action -> "fakeJs".equals(action.attributes().get("property"))));
        assertTrue(analysis.actions().stream().noneMatch(action -> "/fake.do".equals(action.attributes().get("action"))));
    }
}
