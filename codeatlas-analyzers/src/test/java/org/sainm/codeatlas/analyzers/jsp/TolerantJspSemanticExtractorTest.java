package org.sainm.codeatlas.analyzers.jsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.graph.model.Confidence;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TolerantJspSemanticExtractorTest {
    @Test
    void extractsDirectivesActionsExpressionsAndIncludes() {
        String jsp = """
            <%@ page pageEncoding="Shift_JIS" %>
            <%@ taglib prefix="html" uri="http://struts.apache.org/tags-html" %>
            <%@ taglib prefix="app" tagdir="/WEB-INF/tags/app" %>
            <%@ include file="/common/header.jsp" %>
            <jsp:include page="/part.jsp"/>
            <app:field name="userId" label="User"/>
            <jsp:forward page="/done.jsp"/>
            <c:if test="${user != null}">${user.name}</c:if>
            <bean:write name="userForm" property="name"/>
            <logic:iterate id="row" name="users">
              <bean:write name="row" property="id"/>
            </logic:iterate>
            <script>
              const fakeLink = '<html:link action="/fake/from-js.do">Fake</html:link>';
              const fakeCustom = '<app:field name="fakeJs"/>';
              const dynamicUrl = '${ctx}/user/dynamic.do';
            </script>
            <style>
              .fake::after { content: '<html:submit property="fakeCss"/>'; }
            </style>
            <% String value = request.getParameter("id"); %>
            <%= value %>
            """;
        WebAppContext context = new WebAppContext(
            Path.of("."),
            null,
            "2.4",
            "unknown",
            "generic",
            List.of(),
            Map.of("http://struts.apache.org/tags-html", "WEB-INF/struts-html.tld"),
            "UTF-8"
        );

        JspSemanticAnalysis analysis = new TolerantJspSemanticExtractor().extract(jsp, context);

        assertEquals(JspSemanticParserSource.JERICHO_WITH_TOKENIZER_MERGE, analysis.parserSource());
        assertEquals("jericho-html+tolerant-jsp-tokenizer", analysis.parserName());
        assertTrue(analysis.fallbackReason().contains("jsp file path unavailable"));
        assertEquals("Shift_JIS", analysis.encoding());
        assertEquals(List.of("/common/header.jsp"), analysis.includes());
        assertTrue(analysis.taglibs().stream().anyMatch(taglib -> taglib.prefix().equals("html")
            && taglib.uri().equals("http://struts.apache.org/tags-html")
            && taglib.resolvedLocation().equals("WEB-INF/struts-html.tld")
            && taglib.confidence() == Confidence.CERTAIN));
        assertTrue(analysis.taglibs().stream().anyMatch(taglib -> taglib.prefix().equals("app")
            && taglib.tagdir().equals("/WEB-INF/tags/app")
            && taglib.confidence() == Confidence.CERTAIN));
        assertTrue(analysis.directives().stream().anyMatch(directive -> directive.name().equals("page")));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("jsp:include")));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("app:field")
            && action.attributes().get("name").equals("userId")));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("c:if")));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("bean:write")
            && action.attributes().get("property").equals("name")));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("logic:iterate")
            && action.attributes().get("id").equals("row")));
        assertTrue(analysis.actions().stream().noneMatch(action -> "/fake/from-js.do".equals(action.attributes().get("action"))));
        assertTrue(analysis.actions().stream().noneMatch(action -> "fakeJs".equals(action.attributes().get("name"))));
        assertTrue(analysis.actions().stream().noneMatch(action -> "fakeCss".equals(action.attributes().get("property"))));
        assertTrue(analysis.clientNavigations().isEmpty());
        assertTrue(analysis.expressions().stream().anyMatch(expression -> expression.kind().equals("EL")));
        assertTrue(analysis.expressions().stream().anyMatch(expression -> expression.kind().equals("SCRIPTLET")));
        assertTrue(analysis.expressions().stream().anyMatch(expression -> expression.kind().equals("EXPRESSION")));
    }

    @Test
    void extractsStrutsNavigationMessageAndTilesTagsTolerantly() {
        String jsp = """
            <html:link action="/user/edit" paramId="id" paramName="user">Edit</html:link>
            <html:link page="<%= request.getContextPath() %>/legacy.do">Legacy</html:link>
            <html:submit property="save" value="Save"/>
            <html:cancel property="cancel"/>
            <html:button property="preview" value="Preview"/>
            <html:file property="avatar"/>
            <html:image property="imageAction" page="/images/save.png"/>
            <html:reset property="reset"/>
            <html:select property="role">
              <html:optionsCollection name="roleOptions" value="code" label="label"/>
              <html:options collection="statusOptions" property="code" labelProperty="label"/>
              <html:option value="NONE">None</html:option>
            </html:select>
            <html:rewrite action="/user/edit"/>
            <html:img page="/images/edit.png"/>
            <html:errors property="global"/>
            <logic:messagesPresent message="true">
              <html:errors/>
            </logic:messagesPresent>
            <logic:messagesNotPresent name="org.apache.struts.action.ERROR">
              <bean:write name="user" property="name"/>
            </logic:messagesNotPresent>
            <logic:redirect action="/user/search.do"/>
            <logic:forward name="sessionExpired"/>
            <tiles:getAsString name="title"/>
            <tiles:insert definition=".layout.main" flush="true">
              <tiles:put name="body" value="/WEB-INF/jsp/user/body.jsp"/>
            </tiles:insert>
            <form:form action="/spring/user/save" modelAttribute="userForm" method="post">
              <form:input path="name"/>
              <form:select path="role">
                <form:options items="${roleOptions}" itemValue="code" itemLabel="label"/>
              </form:select>
            </form:form>
            """;
        WebAppContext context = new WebAppContext(Path.of("."), null, "2.4", "unknown", "generic", List.of(), Map.of(), "UTF-8");

        JspSemanticAnalysis analysis = new TolerantJspSemanticExtractor().extract(jsp, context);

        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("html:link")
            && "/user/edit".equals(action.attributes().get("action"))
            && "id".equals(action.attributes().get("paramId"))));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("html:link")
            && "<%= request.getContextPath() %>/legacy.do".equals(action.attributes().get("page"))));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("html:submit")
            && "save".equals(action.attributes().get("property"))));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("html:cancel")
            && "cancel".equals(action.attributes().get("property"))));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("html:button")
            && "preview".equals(action.attributes().get("property"))));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("html:file")
            && "avatar".equals(action.attributes().get("property"))));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("html:image")
            && "imageAction".equals(action.attributes().get("property"))));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("html:reset")
            && "reset".equals(action.attributes().get("property"))));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("html:optionsCollection")
            && "roleOptions".equals(action.attributes().get("name"))));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("html:options")
            && "statusOptions".equals(action.attributes().get("collection"))));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("html:option")
            && "NONE".equals(action.attributes().get("value"))));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("html:rewrite")
            && "/user/edit".equals(action.attributes().get("action"))));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("html:img")
            && "/images/edit.png".equals(action.attributes().get("page"))));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("html:errors")
            && "global".equals(action.attributes().get("property"))));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("logic:messagesPresent")
            && "true".equals(action.attributes().get("message"))));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("logic:messagesNotPresent")
            && "org.apache.struts.action.ERROR".equals(action.attributes().get("name"))));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("logic:redirect")
            && "/user/search.do".equals(action.attributes().get("action"))));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("logic:forward")
            && "sessionExpired".equals(action.attributes().get("name"))));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("tiles:getAsString")
            && "title".equals(action.attributes().get("name"))));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("tiles:insert")
            && ".layout.main".equals(action.attributes().get("definition"))));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("tiles:put")
            && "/WEB-INF/jsp/user/body.jsp".equals(action.attributes().get("value"))));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("form:form")
            && "/spring/user/save".equals(action.attributes().get("action"))));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("form:input")
            && "name".equals(action.attributes().get("path"))));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("form:options")
            && "${roleOptions}".equals(action.attributes().get("items"))));
    }

    @Test
    void recordsTokenizerFallbackReasonWhenJasperCannotRun() {
        WebAppContext context = new WebAppContext(
            Path.of("src/main/webapp"),
            null,
            "2.5",
            "2.1",
            "generic",
            List.of(),
            Map.of(),
            "UTF-8"
        );

        JspSemanticAnalysis analysis = new TolerantJspSemanticExtractor().extract("""
            <%@ taglib prefix="html" uri="http://struts.apache.org/tags-html" %>
            <html:form action="/user/save.do"><html:text property="name"/></html:form>
            """, context);

        assertEquals(JspSemanticParserSource.JERICHO_WITH_TOKENIZER_MERGE, analysis.parserSource());
        assertEquals("jericho-html+tolerant-jsp-tokenizer", analysis.parserName());
        assertTrue(analysis.fallbackReason().contains("jsp file path unavailable"));
        assertTrue(analysis.missingContext().contains("jspFile"));
        assertTrue(analysis.missingContext().contains("web.xml"));
        assertTrue(analysis.missingContext().contains("classpathEntries"));
    }
}
