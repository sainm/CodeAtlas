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
        Files.createDirectories(tempDir.resolve("src/main/webapp/common"));
        Files.createDirectories(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user"));
        Files.createDirectories(tempDir.resolve("src/main/webapp/WEB-INF/tags/app"));
        Files.writeString(tempDir.resolve("src/main/webapp/user/header.jsp"), "header");
        Files.writeString(tempDir.resolve("src/main/webapp/common/menu.jsp"), "menu");
        Files.writeString(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/body.jsp"), "body");
        Files.writeString(tempDir.resolve("src/main/webapp/WEB-INF/tags/app/field.tag"), "<%@ tag body-content=\"empty\" %>");
        Files.writeString(jsp, """
            <%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
            <%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
            <%@ taglib prefix="app" tagdir="/WEB-INF/tags/app" %>
            <%@ include file="header.jsp" %>
            <html:form action="/user/save.do" method="post">
              <html:text property="name"/>
              <html:hidden property="id"/>
              <html:select property="role">
                <html:optionsCollection name="roleOptions" value="code" label="label"/>
              </html:select>
              <html:textarea property="description"/>
              <html:radio property="status" value="ACTIVE"/>
              <html:submit property="method" value="save"/>
              <html:file property="avatar"/>
              <html:image property="imageAction" page="/images/save.png"/>
              <html:reset property="reset"/>
            </html:form>
            <html:link action="/user/detail.do" paramId="id" paramName="user">Detail</html:link>
            <html:link page="/help.jsp">Help</html:link>
            <html:link page="<%= request.getContextPath() %>/dynamic.do">Dynamic</html:link>
            <app:field name="name"/>
            <%
              String token = request.getParameter("token");
              Object user = request.getAttribute("currentUser");
              request.setAttribute("screenMode", "edit");
            %>
            <%= request.getParameter("preview") %>
            <script>
              const fakeForm = '<html:form action="/fake/js.do"><html:hidden property="fakeJs"/></html:form>';
              const fakeLink = '<html:link action="/fake/link.do">Fake</html:link>';
              const dynamicUrl = '${ctx}/user/dynamic.do';
            </script>
            <style>
              .fake { content: '<html:hidden property="fakeCss"/>'; }
            </style>
            <jsp:include page="/common/menu.jsp"/>
            <jsp:forward page="/login.jsp"/>
            <logic:redirect action="/user/search.do"/>
            <logic:forward name="sessionExpired"/>
            <logic:present name="currentUser" property="id">
              <bean:write name="currentUser" property="displayName"/>
            </logic:present>
            ${currentUser.email}
            ${requestScope.currentUser.phone}
            ${sessionScope.loginUser.name}
            ${not empty errorMessage}
            <tiles:insert definition=".layout.main" flush="true">
              <tiles:put name="body" value="/WEB-INF/jsp/user/body.jsp"/>
            </tiles:insert>
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "src/main/webapp/user/edit.jsp", tempDir);
        JspAnalysisResult result = new JspFormAnalyzer().analyze(scope, "shop", "src/main/webapp", jsp);

        assertEquals(1, result.forms().size());
        assertEquals(9, result.forms().getFirst().inputs().size());
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.JSP_PAGE));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.JSP_FORM));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.JSP_INPUT
            && node.symbolId().localId().contains("name")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.REQUEST_PARAMETER
            && node.symbolId().ownerQualifiedName().equals("name")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.ACTION_PATH
            && node.symbolId().ownerQualifiedName().equals("user/save")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.ACTION_PATH
            && node.symbolId().ownerQualifiedName().equals("user/detail")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.JSP_PAGE
            && node.symbolId().ownerQualifiedName().equals("help.jsp")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.SUBMITS_TO));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().source().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().kind() == SymbolKind.ACTION_PATH
            && fact.factKey().target().ownerQualifiedName().equals("user/detail")
            && fact.factKey().qualifier().equals("html-link:/user/detail.do")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().source().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().ownerQualifiedName().equals("help.jsp")
            && fact.factKey().qualifier().equals("html-link:/help.jsp")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().source().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().ownerQualifiedName().equals("login.jsp")
            && fact.factKey().qualifier().equals("jsp:forward:/login.jsp")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().source().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().kind() == SymbolKind.ACTION_PATH
            && fact.factKey().target().ownerQualifiedName().equals("user/search")
            && fact.factKey().qualifier().equals("logic-redirect:/user/search.do")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.INCLUDES
            && fact.factKey().target().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().ownerQualifiedName().endsWith("src/main/webapp/user/header.jsp")
            && fact.factKey().qualifier().equals("static_directive:header.jsp")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.INCLUDES
            && fact.factKey().target().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().ownerQualifiedName().endsWith("src/main/webapp/common/menu.jsp")
            && fact.factKey().qualifier().equals("dynamic_action:/common/menu.jsp")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.INCLUDES
            && fact.factKey().target().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().ownerQualifiedName().endsWith("src/main/webapp/WEB-INF/jsp/user/body.jsp")
            && fact.factKey().qualifier().equals("tiles:put:body=/WEB-INF/jsp/user/body.jsp")));
        assertTrue(result.facts().stream().noneMatch(fact -> fact.factKey().qualifier().contains("dynamic.do")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.WRITES_PARAM
            && fact.factKey().qualifier().equals("name")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.WRITES_PARAM
            && fact.factKey().qualifier().equals("description")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.WRITES_PARAM
            && fact.factKey().qualifier().equals("status")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.WRITES_PARAM
            && fact.factKey().qualifier().equals("avatar")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.WRITES_PARAM
            && fact.factKey().qualifier().equals("imageAction")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.WRITES_PARAM
            && fact.factKey().qualifier().equals("reset")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.WRITES_PARAM
            && fact.factKey().source().kind() == SymbolKind.JSP_INPUT
            && fact.factKey().source().localId().contains("method")
            && fact.factKey().target().kind() == SymbolKind.REQUEST_PARAMETER
            && fact.factKey().target().ownerQualifiedName().equals("method")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.DECLARES
            && fact.factKey().target().kind() == SymbolKind.JSP_INPUT
            && fact.factKey().target().localId().contains("link:0:param:id")
            && fact.factKey().qualifier().equals("html-link-param:id")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.WRITES_PARAM
            && fact.factKey().source().kind() == SymbolKind.JSP_INPUT
            && fact.factKey().source().localId().contains("link:0:param:id")
            && fact.factKey().target().kind() == SymbolKind.REQUEST_PARAMETER
            && fact.factKey().target().ownerQualifiedName().equals("id")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.WRITES_PARAM
            && fact.factKey().source().kind() == SymbolKind.JSP_INPUT
            && fact.factKey().source().localId().contains("link:0:param:id")
            && fact.factKey().target().kind() == SymbolKind.ACTION_PATH
            && fact.factKey().target().ownerQualifiedName().equals("user/detail")
            && fact.factKey().qualifier().equals("html-link-param:id")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.USES_CONFIG
            && fact.factKey().source().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().kind() == SymbolKind.CONFIG_KEY
            && fact.factKey().qualifier().equals("jsp-taglib:app=/WEB-INF/tags/app")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.USES_CONFIG
            && fact.factKey().source().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().kind() == SymbolKind.CONFIG_KEY
            && fact.factKey().qualifier().equals("jsp-custom-tag:app:field->WEB-INF/tags/app/field.tag")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.USES_CONFIG
            && fact.factKey().source().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().kind() == SymbolKind.CONFIG_KEY
            && fact.factKey().qualifier().equals("tiles-insert:.layout.main")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.USES_CONFIG
            && fact.factKey().source().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().kind() == SymbolKind.CONFIG_KEY
            && fact.factKey().qualifier().equals("logic-forward:sessionExpired")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.USES_CONFIG
            && fact.factKey().source().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().kind() == SymbolKind.CONFIG_KEY
            && fact.factKey().qualifier().equals("html:optionsCollection:source=roleOptions")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.READS_PARAM
            && fact.factKey().source().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().kind() == SymbolKind.REQUEST_PARAMETER
            && fact.factKey().target().ownerQualifiedName().equals("token")
            && fact.factKey().qualifier().equals("jsp-scriptlet-getParameter:token")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.READS_PARAM
            && fact.factKey().target().kind() == SymbolKind.REQUEST_PARAMETER
            && fact.factKey().target().ownerQualifiedName().equals("currentUser")
            && fact.factKey().qualifier().equals("jsp-scriptlet-getAttribute:currentUser")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.WRITES_PARAM
            && fact.factKey().target().kind() == SymbolKind.REQUEST_PARAMETER
            && fact.factKey().target().ownerQualifiedName().equals("screenMode")
            && fact.factKey().qualifier().equals("jsp-scriptlet-setAttribute:screenMode")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.READS_PARAM
            && fact.factKey().target().kind() == SymbolKind.REQUEST_PARAMETER
            && fact.factKey().target().ownerQualifiedName().equals("preview")
            && fact.factKey().qualifier().equals("jsp-scriptlet-getParameter:preview")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.READS_PARAM
            && fact.factKey().source().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().kind() == SymbolKind.REQUEST_PARAMETER
            && fact.factKey().target().ownerQualifiedName().equals("currentUser.displayName")
            && fact.factKey().qualifier().equals("bean-write:currentUser.displayName")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.READS_PARAM
            && fact.factKey().source().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().kind() == SymbolKind.REQUEST_PARAMETER
            && fact.factKey().target().ownerQualifiedName().equals("currentUser.id")
            && fact.factKey().qualifier().equals("logic-present:currentUser.id")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.READS_PARAM
            && fact.factKey().source().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().kind() == SymbolKind.REQUEST_PARAMETER
            && fact.factKey().target().ownerQualifiedName().equals("currentUser.email")
            && fact.factKey().qualifier().equals("jsp-el:currentUser.email")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.READS_PARAM
            && fact.factKey().source().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().kind() == SymbolKind.REQUEST_PARAMETER
            && fact.factKey().target().ownerQualifiedName().equals("currentUser.phone")
            && fact.factKey().qualifier().equals("jsp-el:currentUser.phone")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.READS_PARAM
            && fact.factKey().source().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().kind() == SymbolKind.REQUEST_PARAMETER
            && fact.factKey().target().ownerQualifiedName().equals("loginUser.name")
            && fact.factKey().qualifier().equals("jsp-el:loginUser.name")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.READS_PARAM
            && fact.factKey().source().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().kind() == SymbolKind.REQUEST_PARAMETER
            && fact.factKey().target().ownerQualifiedName().equals("errorMessage")
            && fact.factKey().qualifier().equals("jsp-el:errorMessage")));
        assertTrue(result.nodes().stream().noneMatch(node -> node.symbolId().kind() == SymbolKind.ACTION_PATH
            && node.symbolId().ownerQualifiedName().equals("fake/js")));
        assertTrue(result.facts().stream().noneMatch(fact -> fact.factKey().qualifier().contains("fake/link.do")));
        assertTrue(result.facts().stream().noneMatch(fact -> fact.factKey().qualifier().contains("fakeJs")));
        assertTrue(result.facts().stream().noneMatch(fact -> fact.factKey().qualifier().contains("fakeCss")));
        assertTrue(result.facts().stream().noneMatch(fact -> fact.factKey().qualifier().contains("dynamic.do")));
    }
}
