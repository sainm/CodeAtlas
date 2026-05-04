package org.sainm.codeatlas.analyzers.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sainm.codeatlas.facts.FactRecord;

class JspFactMapperTest {
    @TempDir
    Path tempDir;

    @Test
    void mapsJspFormsAndInputsToActionPathAndRequestParameterFacts() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/edit.jsp", """
                <html:form action="/user/update.do" method="post">
                  <html:text property="userId"/>
                </html:form>
                """);
        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/edit.jsp")));

        JavaSourceFactBatch batch = JspFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/WEB-INF/jsp/user/edit.jsp"));

        assertFalse(batch.evidence().isEmpty());
        assertFact(batch,
                "CONTAINS",
                "jsp-page://shop/_root/src/main/webapp/WEB-INF/jsp/user/edit.jsp",
                "jsp-form://shop/_root/src/main/webapp/WEB-INF/jsp/user/edit.jsp#form[/user/update.do:post:1:0]");
        assertFact(batch,
                "CONTAINS",
                "jsp-page://shop/_root/src/main/webapp/WEB-INF/jsp/user/edit.jsp",
                "jsp-input://shop/_root/src/main/webapp/WEB-INF/jsp/user/edit.jsp#form[/user/update.do:post:1:0]:input[userId:text:2:0]");
        assertFact(batch,
                "SUBMITS_TO",
                "jsp-form://shop/_root/src/main/webapp/WEB-INF/jsp/user/edit.jsp#form[/user/update.do:post:1:0]",
                "action-path://shop/_root/_actions/user/update");
        assertFact(batch,
                "DECLARES_ENTRYPOINT",
                "jsp-page://shop/_root/src/main/webapp/WEB-INF/jsp/user/edit.jsp",
                "entrypoint://shop/_root/_entrypoints/jsp/WEB-INF/jsp/user/edit.jsp");
        assertFact(batch,
                "BINDS_TO",
                "jsp-input://shop/_root/src/main/webapp/WEB-INF/jsp/user/edit.jsp#form[/user/update.do:post:1:0]:input[userId:text:2:0]",
                "request-param://shop/_root/src/main/webapp/userId");
    }

    @Test
    void mapsDiscoveredJspPagesToEntrypointFacts() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/help.jsp", """
                <%@ page pageEncoding="UTF-8" %>
                <main>Help</main>
                """);
        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/help.jsp")));

        JavaSourceFactBatch batch = JspFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/WEB-INF/jsp/help.jsp"));

        assertFact(batch,
                "DECLARES_ENTRYPOINT",
                "jsp-page://shop/_root/src/main/webapp/WEB-INF/jsp/help.jsp",
                "entrypoint://shop/_root/_entrypoints/jsp/WEB-INF/jsp/help.jsp");
    }

    @Test
    void mapsJspInputsToTheirOwningForms() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/edit.jsp", """
                <form action="/search.do" method="get">
                  <input type="text" name="query"/>
                </form>
                <form action="/user/update.do" method="post">
                  <input type="hidden" name="userId"/>
                </form>
                """);
        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/edit.jsp")));

        JavaSourceFactBatch batch = JspFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/WEB-INF/jsp/user/edit.jsp"));

        assertFact(batch,
                "BINDS_TO",
                "jsp-input://shop/_root/src/main/webapp/WEB-INF/jsp/user/edit.jsp#form[/search.do:get:1:0]:input[query:text:2:0]",
                "request-param://shop/_root/src/main/webapp/query");
        assertFact(batch,
                "BINDS_TO",
                "jsp-input://shop/_root/src/main/webapp/WEB-INF/jsp/user/edit.jsp#form[/user/update.do:post:4:0]:input[userId:hidden:5:0]",
                "request-param://shop/_root/src/main/webapp/userId");
    }

    @Test
    void mapsJspInputsInsideFormsWithQueryActions() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/search.jsp", """
                <form action="/search.do?mode=user#top" method="get">
                  <input type="text" name="query"/>
                </form>
                """);
        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/search.jsp")));

        JavaSourceFactBatch batch = JspFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/WEB-INF/jsp/user/search.jsp"));

        assertFact(batch,
                "BINDS_TO",
                "jsp-input://shop/_root/src/main/webapp/WEB-INF/jsp/user/search.jsp#form[/search.do:get:1:0]:input[query:text:2:0]",
                "request-param://shop/_root/src/main/webapp/query");
    }

    @Test
    void mapsSpringTagInputsToTheirEnclosingSpringForm() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/spring.jsp", """
                <form:form action="/save.do" method="post"><form:input path="name"/></form:form>
                """);
        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/spring.jsp")));

        JavaSourceFactBatch batch = JspFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/WEB-INF/jsp/user/spring.jsp"));

        assertFact(batch,
                "BINDS_TO",
                "jsp-input://shop/_root/src/main/webapp/WEB-INF/jsp/user/spring.jsp#form[/save.do:post:1:0]:input[name:input:1:0]",
                "request-param://shop/_root/src/main/webapp/name");
        assertFalse(batch.facts().stream().anyMatch(fact -> fact.sourceIdentityId().contains("#form[:")));
    }

    @Test
    void mapsDefaultTaglibFormsAndExtensionlessStrutsFormActions() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/taglib-actions.jsp", """
                <html:form action="/user/update"><html:text property="name"/></html:form>
                <form:form action="/api/profile"><form:input path="email"/></form:form>
                """);
        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/taglib-actions.jsp")));

        JavaSourceFactBatch batch = JspFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/WEB-INF/jsp/user/taglib-actions.jsp"));

        assertFact(batch,
                "SUBMITS_TO",
                "jsp-form://shop/_root/src/main/webapp/WEB-INF/jsp/user/taglib-actions.jsp#form[/user/update:post:1:0]",
                "action-path://shop/_root/_actions/user/update");
        assertFact(batch,
                "SUBMITS_TO",
                "jsp-form://shop/_root/src/main/webapp/WEB-INF/jsp/user/taglib-actions.jsp#form[/api/profile:post:2:0]",
                "api-endpoint://shop/_root/_api/POST:/api/profile");
        assertFact(batch,
                "BINDS_TO",
                "jsp-input://shop/_root/src/main/webapp/WEB-INF/jsp/user/taglib-actions.jsp#form[/user/update:post:1:0]:input[name:text:1:0]",
                "request-param://shop/_root/src/main/webapp/name");
    }

    @Test
    void mapsHtmlFormActionsUnderTopLevelStrutsModulePrefixes() throws IOException {
        write("src/main/webapp/admin/WEB-INF/jsp/edit.jsp", """
                <html:form action="/save"><html:text property="name"/></html:form>
                """);
        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/admin/WEB-INF/jsp/edit.jsp")));

        JavaSourceFactBatch batch = JspFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/admin/WEB-INF/jsp/edit.jsp"));

        assertFact(batch,
                "SUBMITS_TO",
                "jsp-form://shop/_root/src/main/webapp/admin/WEB-INF/jsp/edit.jsp#form[/save:post:1:0]",
                "action-path://shop/_root/_actions/admin/save");
    }

    @Test
    void doesNotInferStrutsModulesFromWebInfJspFeatureFolders() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/edit.jsp", """
                <html:form action="/save"><html:text property="name"/></html:form>
                """);
        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/edit.jsp")));

        JavaSourceFactBatch batch = JspFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/WEB-INF/jsp/user/edit.jsp"));

        assertFact(batch,
                "SUBMITS_TO",
                "jsp-form://shop/_root/src/main/webapp/WEB-INF/jsp/user/edit.jsp#form[/save:post:1:0]",
                "action-path://shop/_root/_actions/save");
        assertFalse(batch.facts().stream().anyMatch(fact -> fact.targetIdentityId().equals(
                "action-path://shop/_root/_actions/user/save")));
    }

    @Test
    void mapsJspApiFormsAndParameterReads() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/search.jsp", """
                <form action="/api/users" method="post">
                  <input type="text" name="query"/>
                </form>
                ${param.query}
                <% String page = request.getParameter("page"); %>
                """);
        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/search.jsp")));

        JavaSourceFactBatch batch = JspFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/WEB-INF/jsp/user/search.jsp"));

        assertFact(batch,
                "SUBMITS_TO",
                "jsp-form://shop/_root/src/main/webapp/WEB-INF/jsp/user/search.jsp#form[/api/users:post:1:0]",
                "api-endpoint://shop/_root/_api/POST:/api/users");
        assertFact(batch,
                "READS_REQUEST_PARAM",
                "jsp-page://shop/_root/src/main/webapp/WEB-INF/jsp/user/search.jsp",
                "request-param://shop/_root/src/main/webapp/query");
        assertFact(batch,
                "READS_REQUEST_PARAM",
                "jsp-page://shop/_root/src/main/webapp/WEB-INF/jsp/user/search.jsp",
                "request-param://shop/_root/src/main/webapp/page");
    }

    @Test
    void mapsBlankAndQueryOnlyJspFormActionsToCurrentPage() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/self.jsp", """
                <form method="post"><input name="name"></form>
                <form action="?mode=save" method="get"><input name="query"></form>
                """);
        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/self.jsp")));

        JavaSourceFactBatch batch = JspFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/WEB-INF/jsp/user/self.jsp"));

        assertFact(batch,
                "SUBMITS_TO",
                "jsp-form://shop/_root/src/main/webapp/WEB-INF/jsp/user/self.jsp#form[:post:1:0]",
                "api-endpoint://shop/_root/_api/POST:/WEB-INF/jsp/user/self.jsp");
        assertFact(batch,
                "SUBMITS_TO",
                "jsp-form://shop/_root/src/main/webapp/WEB-INF/jsp/user/self.jsp#form[:get:2:0]",
                "api-endpoint://shop/_root/_api/GET:/WEB-INF/jsp/user/self.jsp");
    }

    @Test
    void mapsJspForwardsToActionPathFacts() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/gate.jsp", """
                <jsp:forward page="/login.do"/>
                """);
        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/gate.jsp")));

        JavaSourceFactBatch batch = JspFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/WEB-INF/jsp/user/gate.jsp"));

        assertFact(batch,
                "FORWARDS_TO",
                "jsp-page://shop/_root/src/main/webapp/WEB-INF/jsp/user/gate.jsp",
                "action-path://shop/_root/_actions/login");
    }

    @Test
    void resolvesRelativeJspForwardTargetsAgainstCurrentPage() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/gate.jsp", """
                <jsp:forward page="login.jsp"/>
                """);
        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/gate.jsp")));

        JavaSourceFactBatch batch = JspFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/WEB-INF/jsp/user/gate.jsp"));

        assertFact(batch,
                "FORWARDS_TO",
                "jsp-page://shop/_root/src/main/webapp/WEB-INF/jsp/user/gate.jsp",
                "jsp-page://shop/_root/src/main/webapp/WEB-INF/jsp/user/login.jsp");
    }

    @Test
    void mapsJspForwardsToStaticHtmlPageFacts() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/gate.jsp", """
                <jsp:forward page="../help.html"/>
                """);
        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/gate.jsp")));

        JavaSourceFactBatch batch = JspFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/WEB-INF/jsp/user/gate.jsp"));

        assertFact(batch,
                "FORWARDS_TO",
                "jsp-page://shop/_root/src/main/webapp/WEB-INF/jsp/user/gate.jsp",
                "html-page://shop/_root/src/main/webapp/WEB-INF/jsp/help.html");
    }

    @Test
    void normalizesAbsoluteJspTargetsWithDotSegments() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/gate.jsp", """
                <form action="/user/../save.do" method="post"><input name="query"></form>
                <jsp:forward page="/WEB-INF/jsp/user/../help.html"/>
                <jsp:include page="/WEB-INF/jsp/user/../common/menu.jsp"/>
                """);
        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/gate.jsp")));

        JavaSourceFactBatch batch = JspFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/WEB-INF/jsp/user/gate.jsp"));

        assertFact(batch,
                "SUBMITS_TO",
                "jsp-form://shop/_root/src/main/webapp/WEB-INF/jsp/user/gate.jsp#form[/save.do:post:1:0]",
                "action-path://shop/_root/_actions/save");
        assertFact(batch,
                "FORWARDS_TO",
                "jsp-page://shop/_root/src/main/webapp/WEB-INF/jsp/user/gate.jsp",
                "html-page://shop/_root/src/main/webapp/WEB-INF/jsp/help.html");
        assertFact(batch,
                "INCLUDES",
                "jsp-page://shop/_root/src/main/webapp/WEB-INF/jsp/user/gate.jsp",
                "jsp-page://shop/_root/src/main/webapp/WEB-INF/jsp/common/menu.jsp");
    }

    @Test
    void normalizesTrailingSlashApiTargetsBeforeMappingFacts() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/gate.jsp", """
                <form action="/api/" method="post"><input name="query"></form>
                <jsp:forward page="/api/"/>
                """);
        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/gate.jsp")));

        JavaSourceFactBatch batch = JspFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/WEB-INF/jsp/user/gate.jsp"));

        assertFact(batch,
                "SUBMITS_TO",
                "jsp-form://shop/_root/src/main/webapp/WEB-INF/jsp/user/gate.jsp#form[/api:post:1:0]",
                "api-endpoint://shop/_root/_api/POST:/api");
        assertFact(batch,
                "FORWARDS_TO",
                "jsp-page://shop/_root/src/main/webapp/WEB-INF/jsp/user/gate.jsp",
                "api-endpoint://shop/_root/_api/GET:/api");
    }

    @Test
    void mapsJspIncludesToTargetPageFacts() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/edit.jsp", """
                <%@ include file="/WEB-INF/jsp/common/header.jspf" %>
                <jsp:include page="menu.jsp"/>
                """);
        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/edit.jsp")));

        JavaSourceFactBatch batch = JspFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/WEB-INF/jsp/user/edit.jsp"));

        assertFact(batch,
                "INCLUDES",
                "jsp-page://shop/_root/src/main/webapp/WEB-INF/jsp/user/edit.jsp",
                "jsp-page://shop/_root/src/main/webapp/WEB-INF/jsp/common/header.jspf");
        assertFact(batch,
                "INCLUDES",
                "jsp-page://shop/_root/src/main/webapp/WEB-INF/jsp/user/edit.jsp",
                "jsp-page://shop/_root/src/main/webapp/WEB-INF/jsp/user/menu.jsp");
    }

    @Test
    void normalizesExternalJspFormTargetsBeforeMappingFacts() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/external.jsp", """
                <form action="https://api.example.com/users?id=1" method="post">
                  <input type="text" name="query"/>
                </form>
                """);
        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/external.jsp")));

        JavaSourceFactBatch batch = JspFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/WEB-INF/jsp/user/external.jsp"));

        assertFact(batch,
                "SUBMITS_TO",
                "jsp-form://shop/_root/src/main/webapp/WEB-INF/jsp/user/external.jsp#form[/external/https/api.example.com/users:post:1:0]",
                "api-endpoint://shop/_root/_api/POST:/external/https/api.example.com/users");
    }

    @Test
    void normalizesExternalJspFormTargetsWithDotSegments() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/external.jsp", """
                <form action="https://api.example.com/../users?id=1" method="post">
                  <input type="text" name="query"/>
                </form>
                """);
        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/external.jsp")));

        JavaSourceFactBatch batch = JspFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/WEB-INF/jsp/user/external.jsp"));

        assertFact(batch,
                "SUBMITS_TO",
                "jsp-form://shop/_root/src/main/webapp/WEB-INF/jsp/user/external.jsp#form[/external/https/api.example.com/users:post:1:0]",
                "api-endpoint://shop/_root/_api/POST:/external/https/api.example.com/users");
    }

    @Test
    void skipsNonRoutableJspFormActions() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/client.jsp", """
                <form action="#" method="post"><input name="anchor"></form>
                <form action="javascript:void(0)" method="post"><input name="noop"></form>
                <form action="mailto:user@example.com" method="post"><input name="mail"></form>
                """);
        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/client.jsp")));

        JavaSourceFactBatch batch = JspFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/WEB-INF/jsp/user/client.jsp"));

        assertFalse(batch.facts().stream().anyMatch(fact -> fact.relationType().name().equals("SUBMITS_TO")));
        assertFalse(batch.facts().stream().anyMatch(fact -> fact.targetIdentityId().startsWith("api-endpoint://")));
    }

    @Test
    void skipsNonHttpAbsoluteJspFormActions() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/external.jsp", """
                <form action="ftp://example.com/upload" method="post"><input name="file"></form>
                """);
        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/external.jsp")));

        JavaSourceFactBatch batch = JspFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/WEB-INF/jsp/user/external.jsp"));

        assertFalse(batch.facts().stream().anyMatch(fact -> fact.relationType().name().equals("SUBMITS_TO")));
        assertFalse(batch.facts().stream().anyMatch(fact -> fact.targetIdentityId().startsWith("api-endpoint://")));
    }

    @Test
    void skipsDynamicJspTargets() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/dynamic.jsp", """
                <form action="${pageContext.request.contextPath}/save.do" method="post"><input name="query"></form>
                <jsp:forward page="${target}"/>
                <jsp:include page="${includeTarget}"/>
                """);
        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/dynamic.jsp")));

        JavaSourceFactBatch batch = JspFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/WEB-INF/jsp/user/dynamic.jsp"));

        assertFalse(batch.facts().stream().anyMatch(fact -> fact.relationType().name().equals("SUBMITS_TO")));
        assertFalse(batch.facts().stream().anyMatch(fact -> fact.relationType().name().equals("FORWARDS_TO")));
        assertFalse(batch.facts().stream().anyMatch(fact -> fact.relationType().name().equals("INCLUDES")));
        assertFalse(batch.facts().stream().anyMatch(fact -> fact.targetIdentityId().contains("${")
                || fact.targetIdentityId().contains("<%")));
    }

    @Test
    void skipsExternalJspForwardAndIncludeTargets() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/external.jsp", """
                <jsp:forward page="https://example.com/help.html"/>
                <jsp:include page="https://example.com/menu.jsp"/>
                """);
        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/external.jsp")));

        JavaSourceFactBatch batch = JspFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/WEB-INF/jsp/user/external.jsp"));

        assertFalse(batch.facts().stream().anyMatch(fact -> fact.relationType().name().equals("FORWARDS_TO")));
        assertFalse(batch.facts().stream().anyMatch(fact -> fact.relationType().name().equals("INCLUDES")));
    }

    @Test
    void mapsSameLineJspInputsToTheirOwningForms() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/min.jsp", """
                <form action="/a.do"><input name="a"></form><form action="/b.do"><input name="b"></form>
                """);
        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/min.jsp")));

        JavaSourceFactBatch batch = JspFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/WEB-INF/jsp/user/min.jsp"));

        assertFact(batch,
                "BINDS_TO",
                "jsp-input://shop/_root/src/main/webapp/WEB-INF/jsp/user/min.jsp#form[/a.do:get:1:0]:input[a:text:1:0]",
                "request-param://shop/_root/src/main/webapp/a");
        assertFact(batch,
                "BINDS_TO",
                "jsp-input://shop/_root/src/main/webapp/WEB-INF/jsp/user/min.jsp#form[/b.do:get:1:44]:input[b:text:1:0]",
                "request-param://shop/_root/src/main/webapp/b");
    }

    @Test
    void doesNotAttachStandaloneJspInputsToFirstForm() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/standalone.jsp", """
                <form action="/a.do"><input name="inside"></form>
                <input name="outside">
                """);
        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/standalone.jsp")));

        JavaSourceFactBatch batch = JspFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/WEB-INF/jsp/user/standalone.jsp"));

        assertFalse(batch.facts().stream().anyMatch(fact -> fact.sourceIdentityId().contains("outside")
                || fact.targetIdentityId().contains("outside")));
    }

    @Test
    void givesSameLineRepeatedJspFormsDistinctKeys() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/repeated.jsp", """
                <form action="/delete.do" method="post"><input name="a"></form><form action="/delete.do" method="post"><input name="b"></form>
                """);
        JspAnalysisResult result = JspSemanticAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/repeated.jsp")));

        JavaSourceFactBatch batch = JspFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/WEB-INF/jsp/user/repeated.jsp"));

        long distinctForms = batch.facts().stream()
                .filter(fact -> fact.relationType().name().equals("BINDS_TO"))
                .map(FactRecord::sourceIdentityId)
                .map(identity -> identity.substring(0, identity.indexOf("]:input[") + 1))
                .distinct()
                .count();
        assertEquals(2, distinctForms);
    }

    private static void assertFact(
            JavaSourceFactBatch batch,
            String relationName,
            String sourceIdentityId,
            String targetIdentityId) {
        assertTrue(batch.facts().stream().anyMatch(fact -> matches(fact, relationName, sourceIdentityId, targetIdentityId)),
                () -> "Missing " + relationName + " fact from " + sourceIdentityId + " to " + targetIdentityId
                        + " in " + batch.facts());
    }

    private static boolean matches(
            FactRecord fact,
            String relationName,
            String sourceIdentityId,
            String targetIdentityId) {
        return fact.relationType().name().equals(relationName)
                && fact.sourceIdentityId().equals(sourceIdentityId)
                && fact.targetIdentityId().equals(targetIdentityId);
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
