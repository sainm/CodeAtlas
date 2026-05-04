package org.sainm.codeatlas.analyzers.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JspSemanticAnalyzerTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    void resetJasperStub() {
        org.apache.jasper.JspC.reset();
    }

    @Test
    void usesWebAppContextToSelectIsolatedJasperProfile() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/simple.jsp", """
                <form action="/user/save.do">
                  <input name="userId">
                </form>
                """);
        WebAppContext context = new WebAppContext(
                tempDir.resolve("src/main/webapp").toString(),
                tempDir.resolve("src/main/webapp/WEB-INF/web.xml").toString(),
                "4.0",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        JasperProfileClassLoaderFactory factory = JasperProfileClassLoaderFactory.using(Map.of(
                "TOMCAT_8_9_JAVAX", Set.of(
                        "org.apache.jasper.JspC",
                        "javax.servlet.ServletContext",
                        "javax.servlet.jsp.JspFactory")));

        JspAnalysisResult result = JspSemanticAnalyzer.using(factory).analyze(
                tempDir.resolve("src/main/webapp"),
                context,
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/simple.jsp")));

        assertEquals(JspParserMode.JASPER, result.parserMode());
        assertEquals(1, org.apache.jasper.JspC.executeCalls);
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("JASPER_ISOLATED_PROFILE_SELECTED")));
    }

    @Test
    void fallsBackToTolerantParserAndExtractsJspPageSemantics() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/edit.jsp", """
                <%@ page pageEncoding="UTF-8" %>
                <%@ taglib prefix="html" uri="http://struts.apache.org/tags-html" %>
                <%@ include file="/WEB-INF/jsp/common/header.jsp" %>
                <jsp:include page="/WEB-INF/jsp/common/menu.jsp"/>
                <jsp:forward page="/login.do"/>
                <html:form action="/user/update.do" method="post">
                  <html:text property="userId"/>
                  <input type="text" name="phone"/>
                  <select name="status"></select>
                  <textarea name="memo"></textarea>
                </html:form>
                ${param.userId}
                <% String phone = request.getParameter("phone"); request.setAttribute("memo", phone); %>
                """);

        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/edit.jsp")));

        assertEquals(JspParserMode.JASPER, result.parserMode());
        assertFalse(result.diagnostics().isEmpty());
        assertTrue(result.directives().stream().anyMatch(directive -> directive.name().equals("page")
                && directive.attributes().get("pageEncoding").equals("UTF-8")));
        assertTrue(result.taglibs().stream().anyMatch(taglib -> taglib.prefix().equals("html")
                && taglib.uri().contains("struts.apache.org")));
        assertTrue(result.includes().stream().anyMatch(include -> include.path().equals("/WEB-INF/jsp/common/header.jsp")
                && include.kind() == JspIncludeKind.STATIC));
        assertTrue(result.includes().stream().anyMatch(include -> include.path().equals("/WEB-INF/jsp/common/menu.jsp")
                && include.kind() == JspIncludeKind.DYNAMIC));
        assertTrue(result.forwards().stream().anyMatch(forward -> forward.path().equals("/login.do")));
        assertTrue(result.forms().stream().anyMatch(form -> form.action().equals("/user/update.do")
                && form.method().equals("post")));
        assertEquals(List.of("userId", "phone", "status", "memo"), result.inputs().stream()
                .map(JspInputInfo::logicalName)
                .toList());
        assertTrue(result.requestParameters().stream().anyMatch(parameter -> parameter.name().equals("userId")
                && parameter.accessKind() == JspRequestParameterAccessKind.READ));
        assertTrue(result.requestParameters().stream().anyMatch(parameter -> parameter.name().equals("phone")
                && parameter.accessKind() == JspRequestParameterAccessKind.READ));
        assertFalse(result.requestParameters().stream().anyMatch(parameter -> parameter.name().equals("memo")));
    }

    @Test
    void extractsUnquotedJspHtmlAttributes() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/unquoted.jsp", """
                <form action=/user/save.do method=post>
                  <input name=userId type=hidden>
                </form>
                """);

        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/unquoted.jsp")));

        assertTrue(result.forms().stream().anyMatch(form -> form.action().equals("/user/save.do")
                && form.method().equals("post")));
        assertTrue(result.inputs().stream().anyMatch(input -> input.logicalName().equals("userId")
                && input.inputType().equals("hidden")));
    }

    @Test
    void defaultsJspTaglibFormsToPostAndPlainInputsToText() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/defaults.jsp", """
                <html:form action="/user/update"><html:text property="name"/></html:form>
                <form:form action="/api/profile"><form:input path="email"/></form:form>
                <form action="/search.do"><input name="query"></form>
                """);

        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/defaults.jsp")));

        assertEquals(List.of("post", "post", "get"), result.forms().stream()
                .map(JspFormInfo::method)
                .toList());
        assertEquals("text", result.inputs().stream()
                .filter(input -> input.logicalName().equals("query"))
                .findFirst()
                .orElseThrow()
                .inputType());
    }

    @Test
    void skipsScriptletElementIncludeAndForwardTargets() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/dynamic.jsp", """
                <jsp:forward page="<%= target %>"/>
                <jsp:include page="<%= menu %>"/>
                """);

        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/dynamic.jsp")));

        assertTrue(result.forwards().isEmpty());
        assertTrue(result.includes().isEmpty());
    }

    @Test
    void ignoresJspCommentBodies() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/commented.jsp", """
                <%-- <jsp:forward page="/login.do"/><jsp:include page="/menu.jsp"/><form action="/old.do"><input name="old">${param.old}<% request.getParameter("hidden"); %> --%>
                """);

        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/commented.jsp")));

        assertTrue(result.forwards().isEmpty());
        assertTrue(result.includes().isEmpty());
        assertTrue(result.forms().isEmpty());
        assertTrue(result.inputs().isEmpty());
        assertTrue(result.requestParameters().isEmpty());
    }

    @Test
    void ignoresMarkupInsideJspCodeBlocks() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/code-markup.jsp", """
                <% String markup = "<form action='/old.do'><input name='old'><jsp:forward page='/old.do'/>"; %>
                <form action="/real.do"><input name="real"></form>
                <jsp:forward page="/real.do"/>
                """);

        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/code-markup.jsp")));

        assertEquals(List.of("/real.do"), result.forms().stream().map(JspFormInfo::action).toList());
        assertEquals(List.of("real"), result.inputs().stream().map(JspInputInfo::logicalName).toList());
        assertEquals(List.of("/real.do"), result.forwards().stream().map(JspForwardInfo::path).toList());
    }

    @Test
    void ignoresFormsInsideHtmlCommentsAndScriptBodies() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/inert-markup.jsp", """
                <!-- <form action="/old.do"><input name="old"></form> -->
                <script>const sample = "<form action='/fake.do'><input name='fake'></form>";</script>
                <form action="/real.do"><input name="real"></form>
                """);

        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/inert-markup.jsp")));

        assertEquals(List.of("/real.do"), result.forms().stream().map(JspFormInfo::action).toList());
        assertEquals(List.of("real"), result.inputs().stream().map(JspInputInfo::logicalName).toList());
    }

    @Test
    void extractsMultiLineJspTagsAndDirectives() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/multiline.jsp", """
                <%@ include
                    file="/WEB-INF/jsp/common/header.jsp" %>
                <jsp:include
                    page="/WEB-INF/jsp/common/menu.jsp"/>
                <jsp:forward
                    page="/login.do"/>
                <form:form
                    action="/save.do"
                    method="post">
                  <form:input
                      path="name"/>
                </form:form>
                """);

        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/multiline.jsp")));

        assertTrue(result.includes().stream().anyMatch(include -> include.path().equals("/WEB-INF/jsp/common/header.jsp")
                && include.kind() == JspIncludeKind.STATIC));
        assertTrue(result.includes().stream().anyMatch(include -> include.path().equals("/WEB-INF/jsp/common/menu.jsp")
                && include.kind() == JspIncludeKind.DYNAMIC));
        assertTrue(result.forwards().stream().anyMatch(forward -> forward.path().equals("/login.do")));
        assertTrue(result.forms().stream().anyMatch(form -> form.action().equals("/save.do")
                && form.method().equals("post")));
        assertTrue(result.inputs().stream().anyMatch(input -> input.logicalName().equals("name")
                && input.sourceTag().equals("form:input")));
    }

    @Test
    void ignoresDirectiveTextInsideJspCodeBlocks() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/code-directive.jsp", """
                <% String sample = "<%@ include file='/old.jsp' %>"; %>
                <%@ include file="/real.jsp" %>
                """);

        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/code-directive.jsp")));

        assertEquals(List.of("/real.jsp"), result.includes().stream()
                .map(JspIncludeInfo::path)
                .toList());
    }

    @Test
    void extractsGetParameterReadsOnlyFromJspCode() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/params.jsp", """
                <p>request.getParameter("text")</p>
                <script>const sample = 'request.getParameter("js")';</script>
                <% String real = request.getParameter("real"); %>
                ${param.el}
                """);

        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/params.jsp")));

        assertEquals(List.of("real", "el"), result.requestParameters().stream()
                .map(JspRequestParameterAccessInfo::name)
                .toList());
    }

    @Test
    void extractsElParamReadsInsideScriptBodies() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/script-el.jsp", """
                <script>
                  const userId = "${param.userId}";
                  const token = "${param['csrf-token']}";
                </script>
                """);

        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/script-el.jsp")));

        assertEquals(List.of("userId", "csrf-token"), result.requestParameters().stream()
                .map(JspRequestParameterAccessInfo::name)
                .toList());
    }

    @Test
    void ignoresGetParameterReadsInsideJspJavaComments() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/commented-params.jsp", """
                <% // request.getParameter("line"); %>
                <%
                  /*
                   request.getParameter("block");
                   */
                  String real = request.getParameter("real");
                %>
                ${param.el}
                """);

        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/commented-params.jsp")));

        assertEquals(List.of("real", "el"), result.requestParameters().stream()
                .map(JspRequestParameterAccessInfo::name)
                .toList());
    }

    @Test
    void ignoresElInsideJspCodeBlocks() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/el-code.jsp", """
                <% String sample = "${param.old}"; %>
                ${param.real}
                """);

        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/el-code.jsp")));

        assertEquals(List.of("real"), result.requestParameters().stream()
                .map(JspRequestParameterAccessInfo::name)
                .toList());
    }

    @Test
    void extractsBracketedElParamReads() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/bracket-param.jsp", """
                ${param['user.id']}
                ${param["user-name"]}
                """);

        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/bracket-param.jsp")));

        assertEquals(List.of("user.id", "user-name"), result.requestParameters().stream()
                .map(JspRequestParameterAccessInfo::name)
                .toList());
    }

    @Test
    void acceptsTagdirTaglibDirectives() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/tagdir.jsp", """
                <%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
                """);

        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/tagdir.jsp")));

        assertTrue(result.taglibs().stream().anyMatch(taglib -> taglib.prefix().equals("ui")
                && taglib.uri().equals("/WEB-INF/tags")));
    }

    @Test
    void parsesJspTagAttributesContainingGreaterThanSigns() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/quoted.jsp", """
                <jsp:include test="${count > 0}" page="/WEB-INF/jsp/common/menu.jsp"/>
                <jsp:forward test="${count > 0}" page="/login.do"/>
                <form:form cssClass='${count > 0 ? "hot" : ""}' action='/save.do' method='post'><form:input disabled='${count > 0}' path='name'/></form:form>
                """);

        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/quoted.jsp")));

        assertTrue(result.includes().stream().anyMatch(include -> include.path().equals("/WEB-INF/jsp/common/menu.jsp")));
        assertTrue(result.forwards().stream().anyMatch(forward -> forward.path().equals("/login.do")));
        assertTrue(result.forms().stream().anyMatch(form -> form.action().equals("/save.do")
                && form.method().equals("post")));
        assertTrue(result.inputs().stream().anyMatch(input -> input.logicalName().equals("name")
                && input.sourceTag().equals("form:input")));
    }

    @Test
    void ignoresCustomElementsStartingWithNativeJspTagNames() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/custom-tags.jsp", """
                <form-input action="/fake.do">
                  <input-field name="fake"></input-field>
                  <select-box name="fakeSelect"></select-box>
                  <textarea-field name="fakeMemo"></textarea-field>
                  <html:text-field property="fakeProperty"/>
                  <form:input-field path="fakePath"/>
                </form-input>
                <form action="/real.do"><input name="real"></form>
                """);

        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/custom-tags.jsp")));

        assertEquals(List.of("/real.do"), result.forms().stream().map(JspFormInfo::action).toList());
        assertEquals(List.of("real"), result.inputs().stream().map(JspInputInfo::logicalName).toList());
    }

    @Test
    void parsesWhitespaceInJspFormEndTags() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/form-close.jsp", """
                <html:form action="/a.do"><html:text property="inside"/></html:form >
                <input name="outside">
                """);

        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/form-close.jsp")));

        JspInputInfo outside = result.inputs().stream()
                .filter(input -> input.logicalName().equals("outside"))
                .findFirst()
                .orElseThrow();
        assertEquals("", outside.formKey());
    }

    @Test
    void invokesJasperBeforeExtractingFallbackSemantics() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/simple.jsp", """
                <form action="/user/save.do"><input name="userId"></form>
                """);

        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/simple.jsp")));

        assertEquals(JspParserMode.JASPER, result.parserMode());
        assertEquals(1, org.apache.jasper.JspC.executeCalls);
        assertEquals(tempDir.resolve("src/main/webapp").toString(), org.apache.jasper.JspC.uriroot);
        assertEquals("WEB-INF/jsp/user/simple.jsp", org.apache.jasper.JspC.jspFiles);
        assertFalse(org.apache.jasper.JspC.outputDir.isBlank());
        assertFalse(org.apache.jasper.JspC.compile);
        assertFalse(org.apache.jasper.JspC.validateXml);
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("JASPER_RUNTIME_PROFILE")
                        && diagnostic.message().contains("profile=TOMCAT_10_JAKARTA")
                        && diagnostic.message().contains("servlet=jakarta")
                        && diagnostic.message().contains("jsp=jakarta")));
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("JASPER_SEMANTIC_PARSE_USED")));
        assertTrue(result.forms().stream().anyMatch(form -> form.action().equals("/user/save.do")));
    }

    @Test
    void contextAwareDefaultsKeepMatchingCurrentJasperRuntime() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/simple.jsp", """
                <form action="/user/save.do"><input name="userId"></form>
                """);
        WebAppContext context = new WebAppContext(
                tempDir.resolve("src/main/webapp").toString(),
                tempDir.resolve("src/main/webapp/WEB-INF/web.xml").toString(),
                "5.0",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                context,
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/simple.jsp")));

        assertEquals(JspParserMode.JASPER, result.parserMode());
        assertEquals(1, org.apache.jasper.JspC.executeCalls);
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("JASPER_RUNTIME_PROFILE")
                        && diagnostic.message().contains("profile=TOMCAT_10_JAKARTA")));
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("JASPER_SEMANTIC_PARSE_USED")));
    }

    @Test
    void fallsBackWhenJasperInvocationFails() throws IOException {
        org.apache.jasper.JspC.executionFailure = new NoClassDefFoundError("jakarta/servlet/ServletContext");
        write("src/main/webapp/WEB-INF/jsp/user/simple.jsp", """
                <form action="/user/save.do"><input name="userId"></form>
                """);

        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/simple.jsp")));

        assertEquals(JspParserMode.TOKEN_FALLBACK, result.parserMode());
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("JASPER_INVOKE_FAILED_TOKEN_FALLBACK")
                        && diagnostic.message().contains("jakarta/servlet/ServletContext")));
        assertTrue(result.forms().stream().anyMatch(form -> form.action().equals("/user/save.do")));
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
