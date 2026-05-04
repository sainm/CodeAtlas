package org.sainm.codeatlas.analyzers.source;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sainm.codeatlas.facts.FactRecord;

class HtmlClientFactMapperTest {
    @TempDir
    Path tempDir;

    @Test
    void mapsHtmlClientStructureAndStaticNavigationFacts() throws IOException {
        write("src/main/webapp/user/edit.html", """
                <form action="/user/update.do" method="post">
                  <input type="text" name="userId">
                </form>
                <a href="/user/list.html">Users</a>
                <button onclick="location.href='/user/list.html'">Back</button>
                <script src="/static/app.js"></script>
                <script>fetch('/api/users', { method: 'POST' });</script>
                """);
        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/edit.html")));

        JavaSourceFactBatch batch = HtmlClientFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/user/edit.html"));

        assertFact(batch,
                "CONTAINS",
                "html-page://shop/_root/src/main/webapp/user/edit.html",
                "html-form://shop/_root/src/main/webapp/user/edit.html#form[/user/update.do:post:1:0]");
        assertFact(batch,
                "DECLARES_ENTRYPOINT",
                "html-page://shop/_root/src/main/webapp/user/edit.html",
                "entrypoint://shop/_root/_entrypoints/html/user/edit.html");
        assertFact(batch,
                "CONTAINS",
                "html-page://shop/_root/src/main/webapp/user/edit.html",
                "html-input://shop/_root/src/main/webapp/user/edit.html#form[/user/update.do:post:1:0]:input[userId:text:2:0]");
        assertFact(batch,
                "CONTAINS",
                "html-page://shop/_root/src/main/webapp/user/edit.html",
                "html-link://shop/_root/src/main/webapp/user/edit.html#link[/user/list.html:4:0]");
        assertFact(batch,
                "SUBMITS_TO",
                "html-form://shop/_root/src/main/webapp/user/edit.html#form[/user/update.do:post:1:0]",
                "action-path://shop/_root/_actions/user/update");
        assertFact(batch,
                "RENDERS_INPUT",
                "html-form://shop/_root/src/main/webapp/user/edit.html#form[/user/update.do:post:1:0]",
                "html-input://shop/_root/src/main/webapp/user/edit.html#form[/user/update.do:post:1:0]:input[userId:text:2:0]");
        assertFact(batch,
                "BINDS_TO",
                "html-input://shop/_root/src/main/webapp/user/edit.html#form[/user/update.do:post:1:0]:input[userId:text:2:0]",
                "request-param://shop/_root/src/main/webapp/userId");
        assertFact(batch,
                "LOADS_SCRIPT",
                "html-page://shop/_root/src/main/webapp/user/edit.html",
                "script-resource://shop/_root/src/main/webapp/static/app.js");
        assertFact(batch,
                "NAVIGATES_TO",
                "html-link://shop/_root/src/main/webapp/user/edit.html#link[/user/list.html:4:0]",
                "html-page://shop/_root/src/main/webapp/user/list.html");
        assertFact(batch,
                "NAVIGATES_TO",
                "dom-event-handler://shop/_root/src/main/webapp/user/edit.html#event[onclick:5:8]",
                "html-page://shop/_root/src/main/webapp/user/list.html");
        assertFact(batch,
                "CALLS_HTTP",
                "client-request://shop/_root/src/main/webapp/user/edit.html#fetch[POST:/api/users:7:0]",
                "api-endpoint://shop/_root/_api/POST:/api/users");
    }

    @Test
    void mapsDiscoveredHtmlPagesToEntrypointFacts() throws IOException {
        write("src/main/webapp/static/help.html", """
                <main>Help</main>
                """);
        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/static/help.html")));

        JavaSourceFactBatch batch = HtmlClientFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/static/help.html"));

        assertFact(batch,
                "DECLARES_ENTRYPOINT",
                "html-page://shop/_root/src/main/webapp/static/help.html",
                "entrypoint://shop/_root/_entrypoints/html/static/help.html");
    }

    @Test
    void normalizesStaticUrlsBeforeMappingFacts() throws IOException {
        write("src/main/webapp/user/edit.html", """
                <script src="/static/app.js?v=1#cache"></script>
                <script>fetch('/api/users?page=1#top');</script>
                <script>fetch('/user/save.do?id=1', { method: 'POST' });</script>
                """);
        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/edit.html")));

        JavaSourceFactBatch batch = HtmlClientFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/user/edit.html"));

        assertFact(batch,
                "LOADS_SCRIPT",
                "html-page://shop/_root/src/main/webapp/user/edit.html",
                "script-resource://shop/_root/src/main/webapp/static/app.js");
        assertFact(batch,
                "CALLS_HTTP",
                "client-request://shop/_root/src/main/webapp/user/edit.html#fetch[GET:/api/users:2:0]",
                "api-endpoint://shop/_root/_api/GET:/api/users");
        assertFact(batch,
                "CALLS_HTTP",
                "client-request://shop/_root/src/main/webapp/user/edit.html#fetch[POST:/user/save.do:3:0]",
                "action-path://shop/_root/_actions/user/save");
    }

    @Test
    void normalizesTrailingSlashApiTargetsBeforeMappingFacts() throws IOException {
        write("src/main/webapp/user/edit.html", """
                <form action="/api/" method="post"><input name="query"></form>
                <script>fetch('/api/');</script>
                """);
        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/edit.html")));

        JavaSourceFactBatch batch = HtmlClientFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/user/edit.html"));

        assertFact(batch,
                "SUBMITS_TO",
                "html-form://shop/_root/src/main/webapp/user/edit.html#form[/api:post:1:0]",
                "api-endpoint://shop/_root/_api/POST:/api");
        assertFact(batch,
                "CALLS_HTTP",
                "client-request://shop/_root/src/main/webapp/user/edit.html#fetch[GET:/api:2:0]",
                "api-endpoint://shop/_root/_api/GET:/api");
    }

    @Test
    void normalizesExternalUrlsBeforeMappingFacts() throws IOException {
        write("src/main/webapp/user/external.html", """
                <script src="https://cdn.example.com/app.js?v=1"></script>
                <a href="https://example.com/help">Help</a>
                <script>fetch('https://api.example.com/users?id=1', { method: 'POST' });</script>
                """);
        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/external.html")));

        JavaSourceFactBatch batch = HtmlClientFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/user/external.html"));

        assertFact(batch,
                "LOADS_SCRIPT",
                "html-page://shop/_root/src/main/webapp/user/external.html",
                "script-resource://shop/_root/src/main/webapp/external/https/cdn.example.com/app.js");
        assertFact(batch,
                "NAVIGATES_TO",
                "html-link://shop/_root/src/main/webapp/user/external.html#link[https://example.com/help:2:0]",
                "api-endpoint://shop/_root/_api/GET:/external/https/example.com/help");
        assertFact(batch,
                "CALLS_HTTP",
                "client-request://shop/_root/src/main/webapp/user/external.html#fetch[POST:/external/https/api.example.com/users:3:0]",
                "api-endpoint://shop/_root/_api/POST:/external/https/api.example.com/users");
    }

    @Test
    void normalizesProtocolRelativeUrlsBeforeMappingFacts() throws IOException {
        write("src/main/webapp/user/protocol.html", """
                <script src="//cdn.example.com/app.js"></script>
                <script>fetch('//api.example.com/users');</script>
                """);
        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/protocol.html")));

        JavaSourceFactBatch batch = HtmlClientFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/user/protocol.html"));

        assertFact(batch,
                "LOADS_SCRIPT",
                "html-page://shop/_root/src/main/webapp/user/protocol.html",
                "script-resource://shop/_root/src/main/webapp/external/protocol-relative/cdn.example.com/app.js");
        assertFact(batch,
                "CALLS_HTTP",
                "client-request://shop/_root/src/main/webapp/user/protocol.html#fetch[GET:/external/protocol-relative/api.example.com/users:2:0]",
                "api-endpoint://shop/_root/_api/GET:/external/protocol-relative/api.example.com/users");
    }

    @Test
    void normalizesExternalHtmlTargetsWithDotSegments() throws IOException {
        write("src/main/webapp/user/external-dots.html", """
                <script src="https://cdn.example.com/../app.js"></script>
                <a href="https://example.com/../help">Help</a>
                <script>fetch('https://api.example.com/../users?id=1', { method: 'POST' });</script>
                """);
        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/external-dots.html")));

        JavaSourceFactBatch batch = HtmlClientFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/user/external-dots.html"));

        assertFact(batch,
                "LOADS_SCRIPT",
                "html-page://shop/_root/src/main/webapp/user/external-dots.html",
                "script-resource://shop/_root/src/main/webapp/external/https/cdn.example.com/app.js");
        assertFact(batch,
                "NAVIGATES_TO",
                "html-link://shop/_root/src/main/webapp/user/external-dots.html#link[https://example.com/../help:2:0]",
                "api-endpoint://shop/_root/_api/GET:/external/https/example.com/help");
        assertFact(batch,
                "CALLS_HTTP",
                "client-request://shop/_root/src/main/webapp/user/external-dots.html#fetch[POST:/external/https/api.example.com/users:3:0]",
                "api-endpoint://shop/_root/_api/POST:/external/https/api.example.com/users");
    }

    @Test
    void skipsInPageAnchorNavigationFacts() throws IOException {
        write("src/main/webapp/user/help.html", """
                <a href="#top">Top</a>
                <a href="">Blank</a>
                """);
        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/help.html")));

        JavaSourceFactBatch batch = HtmlClientFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/user/help.html"));

        assertFalse(batch.facts().stream().anyMatch(fact -> fact.relationType().name().equals("NAVIGATES_TO")));
        assertFalse(batch.facts().stream().anyMatch(fact -> fact.targetIdentityId().equals(
                "api-endpoint://shop/_root/_api/GET:/")));
    }

    @Test
    void skipsNonHttpSchemeNavigationFacts() throws IOException {
        write("src/main/webapp/user/contact.html", """
                <a href="mailto:user@example.com">Mail</a>
                <a href="javascript:void(0)">Noop</a>
                """);
        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/contact.html")));

        JavaSourceFactBatch batch = HtmlClientFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/user/contact.html"));

        assertFalse(batch.facts().stream().anyMatch(fact -> fact.relationType().name().equals("NAVIGATES_TO")));
        assertFalse(batch.facts().stream().anyMatch(fact -> fact.targetIdentityId().startsWith("api-endpoint://")));
    }

    @Test
    void skipsNonHttpAbsoluteHtmlRoutes() throws IOException {
        write("src/main/webapp/user/external.html", """
                <form action="ftp://example.com/upload" method="post"><input name="file"></form>
                <a href="ftp://example.com/help">FTP</a>
                <button onclick="location.href='ftp://example.com/done'">Done</button>
                """);
        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/external.html")));

        JavaSourceFactBatch batch = HtmlClientFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/user/external.html"));

        assertFalse(batch.facts().stream().anyMatch(fact -> fact.relationType().name().equals("SUBMITS_TO")));
        assertFalse(batch.facts().stream().anyMatch(fact -> fact.relationType().name().equals("NAVIGATES_TO")));
    }

    @Test
    void skipsNonRoutableHtmlFormActions() throws IOException {
        write("src/main/webapp/user/client.html", """
                <form action="#" method="post"><input name="anchor"></form>
                <form action="javascript:void(0)" method="post"><input name="noop"></form>
                <form action="mailto:user@example.com" method="post"><input name="mail"></form>
                """);
        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/client.html")));

        JavaSourceFactBatch batch = HtmlClientFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/user/client.html"));

        assertFalse(batch.facts().stream().anyMatch(fact -> fact.relationType().name().equals("SUBMITS_TO")));
        assertFalse(batch.facts().stream().anyMatch(fact -> fact.targetIdentityId().startsWith("api-endpoint://")));
    }

    @Test
    void mapsQueryOnlyHtmlTargetsAgainstCurrentPage() throws IOException {
        write("src/main/webapp/user/edit.html", """
                <form action="?mode=save" method="post"><input name="query"></form>
                <a href="?page=2">Next</a>
                <button onclick="location.href='?done=true'">Done</button>
                <script>fetch('?refresh=true');</script>
                """);
        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/edit.html")));

        JavaSourceFactBatch batch = HtmlClientFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/user/edit.html"));

        assertTrue(batch.facts().stream().anyMatch(fact -> fact.relationType().name().equals("SUBMITS_TO")
                && fact.targetIdentityId().equals("api-endpoint://shop/_root/_api/POST:/user/edit.html")));
        assertTrue(batch.facts().stream().anyMatch(fact -> fact.relationType().name().equals("NAVIGATES_TO")
                && fact.targetIdentityId().equals("html-page://shop/_root/src/main/webapp/user/edit.html")));
        assertTrue(batch.facts().stream().anyMatch(fact -> fact.relationType().name().equals("CALLS_HTTP")
                && fact.targetIdentityId().equals("api-endpoint://shop/_root/_api/GET:/user/edit.html")));
    }

    @Test
    void mapsBlankHtmlFormActionsAgainstCurrentPage() throws IOException {
        write("src/main/webapp/user/edit.html", """
                <form method="post"><input name="query"></form>
                """);
        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/edit.html")));

        JavaSourceFactBatch batch = HtmlClientFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/user/edit.html"));

        assertTrue(batch.facts().stream().anyMatch(fact -> fact.relationType().name().equals("SUBMITS_TO")
                && fact.targetIdentityId().equals("api-endpoint://shop/_root/_api/POST:/user/edit.html")));
    }

    @Test
    void normalizesAbsoluteHtmlTargetsWithDotSegments() throws IOException {
        write("src/main/webapp/user/edit.html", """
                <form action="/user/../save.do" method="post"><input name="query"></form>
                <a href="/docs/../help.html">Help</a>
                <script>fetch('/api/../users', { method: 'POST' });</script>
                """);
        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/edit.html")));

        JavaSourceFactBatch batch = HtmlClientFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/user/edit.html"));

        assertFact(batch,
                "SUBMITS_TO",
                "html-form://shop/_root/src/main/webapp/user/edit.html#form[/save.do:post:1:0]",
                "action-path://shop/_root/_actions/save");
        assertFact(batch,
                "NAVIGATES_TO",
                "html-link://shop/_root/src/main/webapp/user/edit.html#link[/docs/../help.html:2:0]",
                "html-page://shop/_root/src/main/webapp/help.html");
        assertFact(batch,
                "CALLS_HTTP",
                "client-request://shop/_root/src/main/webapp/user/edit.html#fetch[POST:/users:3:0]",
                "api-endpoint://shop/_root/_api/POST:/users");
    }

    @Test
    void mapsClientRequestsToStrutsActionPaths() throws IOException {
        write("src/main/webapp/user/edit.html", """
                <script>
                  fetch('/user/save.do', { method: 'POST' });
                </script>
                <button onclick="fetch('/user/delete.do')">Delete</button>
                """);
        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/edit.html")));

        JavaSourceFactBatch batch = HtmlClientFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/user/edit.html"));

        assertFact(batch,
                "CALLS_HTTP",
                "client-request://shop/_root/src/main/webapp/user/edit.html#fetch[POST:/user/save.do:2:0]",
                "action-path://shop/_root/_actions/user/save");
        assertFact(batch,
                "CALLS_HTTP",
                "dom-event-handler://shop/_root/src/main/webapp/user/edit.html#event[onclick:4:8]",
                "action-path://shop/_root/_actions/user/delete");
    }

    @Test
    void skipsNonHttpFetchTargets() throws IOException {
        write("src/main/webapp/user/data.html", """
                <script>fetch('data:text/plain,ok');</script>
                <button onclick="fetch('blob:https://example.com/id')">Blob</button>
                """);
        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/data.html")));

        JavaSourceFactBatch batch = HtmlClientFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/user/data.html"));

        assertFalse(batch.facts().stream().anyMatch(fact -> fact.relationType().name().equals("CALLS_HTTP")));
    }

    @Test
    void skipsUnsupportedFetchMethodsInsteadOfMappingThemToGet() throws IOException {
        write("src/main/webapp/user/webdav.html", """
                <script>fetch('/webdav', { method: 'PROPFIND' });</script>
                <button onclick="fetch('/webdav-event', { method: 'PROPFIND' })">Inspect</button>
                """);
        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/webdav.html")));

        JavaSourceFactBatch batch = HtmlClientFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/user/webdav.html"));

        assertFalse(batch.facts().stream().anyMatch(fact -> fact.relationType().name().equals("CALLS_HTTP")));
        assertFalse(batch.facts().stream().anyMatch(fact -> fact.targetIdentityId().contains("GET:/webdav")));
    }

    @Test
    void givesSameLineDomEventHandlersDistinctIdentities() throws IOException {
        write("src/main/webapp/user/events.html", """
                <button onclick="fetch('/a.do')">A</button><button onclick="fetch('/b.do')">B</button>
                """);
        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/events.html")));

        JavaSourceFactBatch batch = HtmlClientFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/user/events.html"));

        long distinctHandlers = batch.facts().stream()
                .filter(fact -> fact.relationType().name().equals("CALLS_HTTP"))
                .map(FactRecord::sourceIdentityId)
                .distinct()
                .count();
        assertEquals(2, distinctHandlers);
    }

    @Test
    void mapsHtmlInputsToTheirOwningForms() throws IOException {
        write("src/main/webapp/user/edit.html", """
                <form action="/search.do" method="get">
                  <input type="text" name="query">
                </form>
                <form action="/user/update.do" method="post">
                  <input type="hidden" name="userId">
                </form>
                """);
        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/edit.html")));

        JavaSourceFactBatch batch = HtmlClientFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/user/edit.html"));

        assertFact(batch,
                "RENDERS_INPUT",
                "html-form://shop/_root/src/main/webapp/user/edit.html#form[/search.do:get:1:0]",
                "html-input://shop/_root/src/main/webapp/user/edit.html#form[/search.do:get:1:0]:input[query:text:2:0]");
        assertFact(batch,
                "RENDERS_INPUT",
                "html-form://shop/_root/src/main/webapp/user/edit.html#form[/user/update.do:post:4:0]",
                "html-input://shop/_root/src/main/webapp/user/edit.html#form[/user/update.do:post:4:0]:input[userId:hidden:5:0]");
    }

    @Test
    void mapsHtmlInputsInsideFormsWithQueryActions() throws IOException {
        write("src/main/webapp/user/search.html", """
                <form action="/search.do?mode=user#top" method="get">
                  <input type="text" name="query">
                </form>
                """);
        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/search.html")));

        JavaSourceFactBatch batch = HtmlClientFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/user/search.html"));

        assertFact(batch,
                "RENDERS_INPUT",
                "html-form://shop/_root/src/main/webapp/user/search.html#form[/search.do:get:1:0]",
                "html-input://shop/_root/src/main/webapp/user/search.html#form[/search.do:get:1:0]:input[query:text:2:0]");
        assertFact(batch,
                "BINDS_TO",
                "html-input://shop/_root/src/main/webapp/user/search.html#form[/search.do:get:1:0]:input[query:text:2:0]",
                "request-param://shop/_root/src/main/webapp/query");
    }

    @Test
    void mapsSameLineHtmlInputsToTheirOwningForms() throws IOException {
        write("src/main/webapp/user/min.html", """
                <form action="/a.do"><input name="a"></form><form action="/b.do"><input name="b"></form>
                """);
        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/min.html")));

        JavaSourceFactBatch batch = HtmlClientFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/user/min.html"));

        assertFact(batch,
                "RENDERS_INPUT",
                "html-form://shop/_root/src/main/webapp/user/min.html#form[/a.do:get:1:0]",
                "html-input://shop/_root/src/main/webapp/user/min.html#form[/a.do:get:1:0]:input[a:text:1:0]");
        assertFact(batch,
                "RENDERS_INPUT",
                "html-form://shop/_root/src/main/webapp/user/min.html#form[/b.do:get:1:44]",
                "html-input://shop/_root/src/main/webapp/user/min.html#form[/b.do:get:1:44]:input[b:text:1:0]");
    }

    @Test
    void doesNotAttachStandaloneHtmlInputsToFirstForm() throws IOException {
        write("src/main/webapp/user/standalone.html", """
                <form action="/a.do"><input name="inside"></form>
                <input name="outside">
                """);
        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/standalone.html")));

        JavaSourceFactBatch batch = HtmlClientFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/user/standalone.html"));

        assertFalse(batch.facts().stream().anyMatch(fact -> fact.sourceIdentityId().contains("outside")
                || fact.targetIdentityId().contains("outside")));
    }

    @Test
    void resolvesRelativeHtmlTargetsAgainstTheOwningPage() throws IOException {
        write("src/main/webapp/user/edit.html", """
                <a href="list.html">Users</a>
                <script src="app.js"></script>
                <script>fetch('save.do', { method: 'POST' });</script>
                """);
        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/edit.html")));

        JavaSourceFactBatch batch = HtmlClientFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/user/edit.html"));

        assertFact(batch,
                "LOADS_SCRIPT",
                "html-page://shop/_root/src/main/webapp/user/edit.html",
                "script-resource://shop/_root/src/main/webapp/user/app.js");
        assertFact(batch,
                "NAVIGATES_TO",
                "html-link://shop/_root/src/main/webapp/user/edit.html#link[list.html:1:0]",
                "html-page://shop/_root/src/main/webapp/user/list.html");
        assertFact(batch,
                "CALLS_HTTP",
                "client-request://shop/_root/src/main/webapp/user/edit.html#fetch[POST:/user/save.do:3:0]",
                "action-path://shop/_root/_actions/user/save");
    }

    @Test
    void givesSameLineRepeatedHtmlFormsDistinctKeys() throws IOException {
        write("src/main/webapp/user/repeated.html", """
                <form action="/delete.do" method="post"><input name="a"></form><form action="/delete.do" method="post"><input name="b"></form>
                """);
        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/repeated.html")));

        JavaSourceFactBatch batch = HtmlClientFactMapper.defaults().map(
                result,
                new JavaSourceFactContext(
                        "shop",
                        "_root",
                        "src/main/webapp",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/webapp/user/repeated.html"));

        long distinctForms = batch.facts().stream()
                .filter(fact -> fact.relationType().name().equals("RENDERS_INPUT"))
                .map(FactRecord::sourceIdentityId)
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
