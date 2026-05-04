package org.sainm.codeatlas.analyzers.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HtmlClientAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsStaticFormsLinksScriptsAndClientRequests() throws IOException {
        write("src/main/webapp/user/edit.html", """
                <html>
                  <body>
                    <form action="/user/update.do" method="post">
                      <input type="text" name="userId">
                    </form>
                    <a href="/user/list.html">Users</a>
                    <button onclick="location.href='/user/list.html'">Back</button>
                    <script src="/static/app.js"></script>
                    <script>
                      fetch('/api/users', { method: 'POST' });
                      fetch('/api/profile');
                    </script>
                  </body>
                </html>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/edit.html")));

        assertTrue(result.pages().stream().anyMatch(page -> page.path().equals("user/edit.html")));
        assertTrue(result.forms().stream().anyMatch(form -> form.action().equals("/user/update.do")
                && form.method().equals("post")));
        assertEquals(List.of("userId"), result.inputs().stream().map(HtmlInputInfo::logicalName).toList());
        assertTrue(result.links().stream().anyMatch(link -> link.href().equals("/user/list.html")));
        assertTrue(result.scripts().stream().anyMatch(script -> script.src().equals("/static/app.js")));
        assertTrue(result.clientRequests().stream().anyMatch(request -> request.url().equals("/api/users")
                && request.httpMethod().equals("POST")));
        assertTrue(result.clientRequests().stream().anyMatch(request -> request.url().equals("/api/profile")
                && request.httpMethod().equals("GET")));
        assertTrue(result.domEventHandlers().stream().anyMatch(handler -> handler.eventName().equals("onclick")
                && handler.relationName().equals("NAVIGATES_TO")
                && handler.target().equals("/user/list.html")));
    }

    @Test
    void ignoresInlineBodyOfExternalScriptTags() throws IOException {
        write("src/main/webapp/user/external-script.html", """
                <script src="/static/app.js">
                  fetch('/api/ignored');
                </script>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/external-script.html")));

        assertTrue(result.scripts().stream().anyMatch(script -> script.src().equals("/static/app.js")));
        assertTrue(result.clientRequests().isEmpty());
    }

    @Test
    void ignoresDynamicJavaScriptRequestUrls() throws IOException {
        write("src/main/webapp/user/dynamic.html", """
                <script>
                  const base = '/api/';
                  fetch(base + 'users', { method: 'POST' });
                </script>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/dynamic.html")));

        assertTrue(result.clientRequests().isEmpty());
        assertTrue(result.domEventHandlers().isEmpty());
    }

    @Test
    void ignoresFetchUrlsBuiltFromLiteralPrefixes() throws IOException {
        write("src/main/webapp/user/dynamic.html", """
                <script>
                  fetch('/api/users/' + userId, { method: 'DELETE' });
                </script>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/dynamic.html")));

        assertTrue(result.clientRequests().isEmpty());
        assertTrue(result.domEventHandlers().isEmpty());
    }

    @Test
    void ignoresArbitraryMemberFetchCalls() throws IOException {
        write("src/main/webapp/user/member-fetch.html", """
                <script>
                  api.fetch('/api/fake');
                  window.fetch('/api/real');
                </script>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/member-fetch.html")));

        assertEquals(List.of("/api/real"), result.clientRequests().stream().map(ClientRequestInfo::url).toList());
    }

    @Test
    void extractsClientRequestsFromStandaloneJavaScriptFiles() throws IOException {
        write("src/main/webapp/static/app.js", """
                fetch('/api/orders', { method: 'POST' });
                fetch('/api/profile');
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/static/app.js")));

        assertEquals(List.of("/api/orders", "/api/profile"), result.clientRequests().stream()
                .map(ClientRequestInfo::url)
                .toList());
        assertEquals(List.of("POST", "GET"), result.clientRequests().stream()
                .map(ClientRequestInfo::httpMethod)
                .toList());
    }

    @Test
    void keepsStandaloneJavaScriptExecutableAfterScriptCloseStringLiterals() throws IOException {
        write("src/main/webapp/static/app.js", """
                const sample = "</script>";
                fetch('/api/orders', { method: 'POST' });
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/static/app.js")));

        assertEquals(List.of("/api/orders"), result.clientRequests().stream()
                .map(ClientRequestInfo::url)
                .toList());
    }

    @Test
    void skipsHtmlExtractionFromStandaloneJavaScriptTemplateText() throws IOException {
        write("src/main/webapp/static/app.js", """
                const sample = "</script><form action='/old.do'><input name='old'>";
                fetch('/api/orders');
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/static/app.js")));

        assertEquals(List.of("/api/orders"), result.clientRequests().stream()
                .map(ClientRequestInfo::url)
                .toList());
        assertTrue(result.forms().isEmpty());
        assertTrue(result.inputs().isEmpty());
        assertTrue(result.links().isEmpty());
        assertTrue(result.scripts().isEmpty());
        assertTrue(result.domEventHandlers().isEmpty());
    }

    @Test
    void ignoresFetchCallsWithDynamicRequestOptions() throws IOException {
        write("src/main/webapp/user/dynamic-options.html", """
                <script>
                  fetch('/api/users', requestOptions);
                  fetch('/api/audit', { method });
                </script>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/dynamic-options.html")));

        assertTrue(result.clientRequests().isEmpty());
        assertTrue(result.domEventHandlers().isEmpty());
    }

    @Test
    void ignoresFetchCallsWithDynamicMethodExpressions() throws IOException {
        write("src/main/webapp/user/dynamic-method.html", """
                <script>
                  fetch('/api/users', { method: 'POST' + suffix });
                </script>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/dynamic-method.html")));

        assertTrue(result.clientRequests().isEmpty());
        assertTrue(result.domEventHandlers().isEmpty());
    }

    @Test
    void ignoresFetchCallsWithRequestInitSpreads() throws IOException {
        write("src/main/webapp/user/dynamic-options.html", """
                <script>
                  fetch('/api/users', { ...requestOptions });
                  fetch('/api/audit', { method: 'POST', ...requestOptions });
                </script>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/dynamic-options.html")));

        assertTrue(result.clientRequests().isEmpty());
        assertTrue(result.domEventHandlers().isEmpty());
    }

    @Test
    void extractsFetchMethodsAfterTemplateLiteralOptionValues() throws IOException {
        write("src/main/webapp/user/template-options.html", """
                <script>
                  fetch('/api/users', { body: `...`, method: 'POST' });
                </script>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/template-options.html")));

        assertTrue(result.clientRequests().stream().anyMatch(request -> request.url().equals("/api/users")
                && request.httpMethod().equals("POST")));
    }

    @Test
    void extractsMultiLineFetchCallsInScripts() throws IOException {
        write("src/main/webapp/user/multiline.html", """
                <script>
                  fetch('/api/users', {
                    method: 'POST'
                  });
                </script>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/multiline.html")));

        assertTrue(result.clientRequests().stream().anyMatch(request -> request.url().equals("/api/users")
                && request.httpMethod().equals("POST")));
    }

    @Test
    void extractsFetchCallsSplitAfterOpeningParen() throws IOException {
        write("src/main/webapp/user/multiline.html", """
                <script>
                  fetch(
                    '/api/users',
                    { method: 'PUT' }
                  );
                </script>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/multiline.html")));

        assertTrue(result.clientRequests().stream().anyMatch(request -> request.url().equals("/api/users")
                && request.httpMethod().equals("PUT")));
    }

    @Test
    void treatsInlineHandlerFetchesAsDomEventsOnly() throws IOException {
        write("src/main/webapp/user/events.html", """
                <button onclick="fetch('/user/delete.do')">Delete</button>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/events.html")));

        assertTrue(result.clientRequests().isEmpty());
        assertTrue(result.domEventHandlers().stream().anyMatch(handler -> handler.eventName().equals("onclick")
                && handler.relationName().equals("CALLS_HTTP")
                && handler.target().equals("/user/delete.do")));
    }

    @Test
    void extractsInlineHandlersWithGreaterThanComparisons() throws IOException {
        write("src/main/webapp/user/events.html", """
                <button onclick="if (count > 0) fetch('/user/delete.do')">Delete</button>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/events.html")));

        assertTrue(result.clientRequests().isEmpty());
        assertTrue(result.domEventHandlers().stream().anyMatch(handler -> handler.eventName().equals("onclick")
                && handler.relationName().equals("CALLS_HTTP")
                && handler.target().equals("/user/delete.do")));
    }

    @Test
    void extractsMultiLineHtmlTagsAndHandlers() throws IOException {
        write("src/main/webapp/user/multiline-tags.html", """
                <form
                    action="/user/save.do"
                    method="post">
                  <input
                      name="userId"
                      type="hidden">
                </form>
                <a
                    href="/user/list.html">Users</a>
                <button
                    onclick="fetch('/audit.do'); location.href='/done.html'">Done</button>
                <script
                    src="/static/app.js"></script>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/multiline-tags.html")));

        assertTrue(result.forms().stream().anyMatch(form -> form.action().equals("/user/save.do")
                && form.method().equals("post")));
        assertTrue(result.inputs().stream().anyMatch(input -> input.logicalName().equals("userId")
                && input.inputType().equals("hidden")));
        assertTrue(result.links().stream().anyMatch(link -> link.href().equals("/user/list.html")));
        assertTrue(result.scripts().stream().anyMatch(script -> script.src().equals("/static/app.js")));
        assertTrue(result.domEventHandlers().stream().anyMatch(handler -> handler.relationName().equals("CALLS_HTTP")
                && handler.target().equals("/audit.do")));
        assertTrue(result.domEventHandlers().stream().anyMatch(handler -> handler.relationName().equals("NAVIGATES_TO")
                && handler.target().equals("/done.html")));
    }

    @Test
    void mapsEmptyFetchAndNavigationTargetsToCurrentPageSentinel() throws IOException {
        write("src/main/webapp/user/current.html", """
                <button onclick="location.href=''">Reload</button>
                <script>fetch('');</script>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/current.html")));

        assertEquals(List.of("?"), result.clientRequests().stream().map(ClientRequestInfo::url).toList());
        assertEquals(List.of("?"), result.domEventHandlers().stream()
                .filter(handler -> handler.relationName().equals("NAVIGATES_TO"))
                .map(DomEventHandlerInfo::target)
                .toList());
    }

    @Test
    void ignoresCustomElementsStartingWithNativeHtmlTagNames() throws IOException {
        write("src/main/webapp/user/custom-tags.html", """
                <form-input action="/fake.do">
                  <input-field name="fake"></input-field>
                  <select-box name="fakeSelect"></select-box>
                  <textarea-field name="fakeMemo"></textarea-field>
                  <a-card href="/fake.html"></a-card>
                  <script-widget src="/fake.js"></script-widget>
                </form-input>
                <form action="/real.do"><input name="real"></form>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/custom-tags.html")));

        assertEquals(List.of("/real.do"), result.forms().stream().map(HtmlFormInfo::action).toList());
        assertEquals(List.of("real"), result.inputs().stream().map(HtmlInputInfo::logicalName).toList());
        assertTrue(result.links().isEmpty());
        assertTrue(result.scripts().isEmpty());
    }

    @Test
    void ignoresFetchExamplesOutsideScriptsAndHandlers() throws IOException {
        write("src/main/webapp/user/docs.html", """
                <pre>fetch('/api/example')</pre>
                <div data-code="fetch('/api/data')">Example</div>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/docs.html")));

        assertTrue(result.clientRequests().isEmpty());
        assertTrue(result.domEventHandlers().isEmpty());
    }

    @Test
    void ignoresHandlerLikeDataAttributes() throws IOException {
        write("src/main/webapp/user/data.html", """
                <button data-onclick="fetch('/user/delete.do')">Delete</button>
                <button x-onclick="location.href='/user/list.html'">Back</button>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/data.html")));

        assertTrue(result.clientRequests().isEmpty());
        assertTrue(result.domEventHandlers().isEmpty());
    }

    @Test
    void ignoresHandlerExamplesOutsideHtmlTags() throws IOException {
        write("src/main/webapp/user/docs.html", """
                <pre>onclick="fetch('/api/example')"</pre>
                <code>onsubmit="location.href='/done.html'"</code>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/docs.html")));

        assertTrue(result.clientRequests().isEmpty());
        assertTrue(result.domEventHandlers().isEmpty());
    }

    @Test
    void ignoresFetchExamplesInNonJavaScriptScriptBlocks() throws IOException {
        write("src/main/webapp/user/templates.html", """
                <script type="application/json">
                  {"example": "fetch('/api/example')"}
                </script>
                <script type="text/template">fetch('/api/template')</script>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/templates.html")));

        assertTrue(result.clientRequests().isEmpty());
        assertTrue(result.domEventHandlers().isEmpty());
    }

    @Test
    void ignoresHtmlMarkupExamplesInsideScriptBlocks() throws IOException {
        write("src/main/webapp/user/templates.html", """
                <script>
                  const template = "<form action='/fake.do'><input name='fake'><a href='/fake.html'>Fake</a>";
                </script>
                <form action="/real.do"><input name="real"></form>
                <a href="/real.html">Real</a>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/templates.html")));

        assertEquals(List.of("/real.do"), result.forms().stream().map(HtmlFormInfo::action).toList());
        assertEquals(List.of("real"), result.inputs().stream().map(HtmlInputInfo::logicalName).toList());
        assertEquals(List.of("/real.html"), result.links().stream().map(HtmlLinkInfo::href).toList());
    }

    @Test
    void ignoresMarkupInsideHtmlComments() throws IOException {
        write("src/main/webapp/user/commented.html", """
                <!-- <form action="/old.do"><input name="old"><a href="/old.html">Old</a> -->
                <form action="/real.do"><input name="real"></form>
                <a href="/real.html">Real</a>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/commented.html")));

        assertEquals(List.of("/real.do"), result.forms().stream().map(HtmlFormInfo::action).toList());
        assertEquals(List.of("real"), result.inputs().stream().map(HtmlInputInfo::logicalName).toList());
        assertEquals(List.of("/real.html"), result.links().stream().map(HtmlLinkInfo::href).toList());
    }

    @Test
    void recordsFetchesInHandlersThatAlsoNavigate() throws IOException {
        write("src/main/webapp/user/composite.html", """
                <button onclick="fetch('/audit.do'); location.href='/done.html'">Done</button>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/composite.html")));

        assertTrue(result.domEventHandlers().stream().anyMatch(handler -> handler.eventName().equals("onclick")
                && handler.relationName().equals("CALLS_HTTP")
                && handler.target().equals("/audit.do")));
        assertTrue(result.domEventHandlers().stream().anyMatch(handler -> handler.eventName().equals("onclick")
                && handler.relationName().equals("NAVIGATES_TO")
                && handler.target().equals("/done.html")));
    }

    @Test
    void ignoresLocationHrefTargetsBuiltFromLiteralPrefixes() throws IOException {
        write("src/main/webapp/user/dynamic.html", """
                <button onclick="location.href='/user/' + userId">Open</button>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/dynamic.html")));

        assertTrue(result.clientRequests().isEmpty());
        assertTrue(result.domEventHandlers().isEmpty());
    }

    @Test
    void ignoresObjectLocationHrefAssignments() throws IOException {
        write("src/main/webapp/user/object-location.html", """
                <button onclick="state.location.href='/old.html'; window.location.href='/real.html'">Open</button>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/object-location.html")));

        assertEquals(List.of("/real.html"), result.domEventHandlers().stream()
                .filter(handler -> handler.relationName().equals("NAVIGATES_TO"))
                .map(DomEventHandlerInfo::target)
                .toList());
    }

    @Test
    void ignoresLocationHrefExamplesInsideHandlerComments() throws IOException {
        write("src/main/webapp/user/commented-handler.html", """
                <button onclick="// location.href='/old.html'">Open</button>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/commented-handler.html")));

        assertTrue(result.clientRequests().isEmpty());
        assertTrue(result.domEventHandlers().isEmpty());
    }

    @Test
    void extractsFetchCallsWithNestedOptions() throws IOException {
        write("src/main/webapp/user/options.html", """
                <script>
                  fetch('/api/users', { method: 'POST', headers: { Accept: 'application/json' } });
                </script>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/options.html")));

        assertTrue(result.clientRequests().stream().anyMatch(request -> request.url().equals("/api/users")
                && request.httpMethod().equals("POST")));
    }

    @Test
    void extractsFetchCallsWithCommentsBeforeUrl() throws IOException {
        write("src/main/webapp/user/commented-argument.html", """
                <script>
                  fetch(/* api */ '/api/users', { method: 'POST' });
                </script>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/commented-argument.html")));

        assertTrue(result.clientRequests().stream().anyMatch(request -> request.url().equals("/api/users")
                && request.httpMethod().equals("POST")));
    }

    @Test
    void extractsFetchCallsWithCommentsBeforeCallParenthesis() throws IOException {
        write("src/main/webapp/user/commented-call.html", """
                <script>
                  fetch /* audit */ ('/api/users', { method: 'POST' });
                </script>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/commented-call.html")));

        assertTrue(result.clientRequests().stream().anyMatch(request -> request.url().equals("/api/users")
                && request.httpMethod().equals("POST")));
    }

    @Test
    void parsesWhitespaceInHtmlScriptEndTags() throws IOException {
        write("src/main/webapp/user/script-close.html", """
                <script>fetch('/api/init');</script >
                <form action="/real.do"><input name="real"></form>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/script-close.html")));

        assertEquals(List.of("/real.do"), result.forms().stream().map(HtmlFormInfo::action).toList());
        assertEquals(List.of("real"), result.inputs().stream().map(HtmlInputInfo::logicalName).toList());
    }

    @Test
    void parsesWhitespaceInHtmlFormEndTags() throws IOException {
        write("src/main/webapp/user/form-close.html", """
                <form action="/a.do"><input name="inside"></form >
                <input name="outside">
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/form-close.html")));

        HtmlInputInfo outside = result.inputs().stream()
                .filter(input -> input.logicalName().equals("outside"))
                .findFirst()
                .orElseThrow();
        assertEquals("", outside.formKey());
    }

    @Test
    void extractsFetchMethodFromQuotedOptionsKey() throws IOException {
        write("src/main/webapp/user/options.html", """
                <script>
                  fetch('/api/users', { "method": "DELETE" });
                </script>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/options.html")));

        assertTrue(result.clientRequests().stream().anyMatch(request -> request.url().equals("/api/users")
                && request.httpMethod().equals("DELETE")));
    }

    @Test
    void ignoresJavaScriptCommentsWhenMatchingFetchCallParentheses() throws IOException {
        write("src/main/webapp/user/options.html", """
                <script>
                  fetch('/api/users', {
                    // ) this is not the fetch call close
                    method: 'POST'
                  });
                </script>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/options.html")));

        assertTrue(result.clientRequests().stream().anyMatch(request -> request.url().equals("/api/users")
                && request.httpMethod().equals("POST")));
    }

    @Test
    void ignoresMethodTextInsideFetchOptionStrings() throws IOException {
        write("src/main/webapp/user/options.html", """
                <script>
                  fetch('/api/users', { headers: { "X-Example": "method: 'POST'" } });
                </script>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/options.html")));

        assertTrue(result.clientRequests().stream().anyMatch(request -> request.url().equals("/api/users")
                && request.httpMethod().equals("GET")));
    }

    @Test
    void ignoresFetchExamplesInsideJavaScriptStringsAndComments() throws IOException {
        write("src/main/webapp/user/examples.html", """
                <script>
                  const sample = "fetch('/api/string')";
                  const template = `fetch('/api/template')`;
                  // fetch('/api/comment')
                  /*
                   * fetch('/api/block')
                   */
                  fetch('/api/real');
                </script>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/examples.html")));

        assertEquals(List.of("/api/real"), result.clientRequests().stream().map(ClientRequestInfo::url).toList());
    }

    @Test
    void extractsStaticTemplateLiteralFetchesAndNavigations() throws IOException {
        write("src/main/webapp/user/template-literals.html", """
                <button onclick="location.href=`/done.html`">Done</button>
                <script>
                  fetch(`/api/users`, { method: `POST` });
                </script>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/template-literals.html")));

        assertEquals(List.of("/api/users"), result.clientRequests().stream().map(ClientRequestInfo::url).toList());
        assertEquals(List.of("POST"), result.clientRequests().stream().map(ClientRequestInfo::httpMethod).toList());
        assertEquals(List.of("/done.html"), result.domEventHandlers().stream()
                .filter(handler -> handler.relationName().equals("NAVIGATES_TO"))
                .map(DomEventHandlerInfo::target)
                .toList());
    }

    @Test
    void ignoresDynamicTemplateLiteralFetchesAndNavigations() throws IOException {
        write("src/main/webapp/user/template-literals.html", """
                <button onclick="location.href=`/user/${id}`">Open</button>
                <script>
                  fetch(`/api/users/${id}`, { method: 'DELETE' });
                  fetch('/api/audit', { method: `${verb}` });
                </script>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/template-literals.html")));

        assertTrue(result.clientRequests().isEmpty());
        assertTrue(result.domEventHandlers().isEmpty());
    }

    @Test
    void parsesHtmlTagAttributesContainingGreaterThanSigns() throws IOException {
        write("src/main/webapp/user/quoted.html", """
                <form data-rule='amount > 0' action='/user/save.do' method='post'>
                  <input data-rule="age > 18" name="age" type="number">
                </form>
                <a data-rule='count > 0' href='/user/list.html'>Users</a>
                <script data-rule='a > b' type='application/json'>fetch('/api/example')</script>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/quoted.html")));

        assertTrue(result.forms().stream().anyMatch(form -> form.action().equals("/user/save.do")
                && form.method().equals("post")));
        assertTrue(result.inputs().stream().anyMatch(input -> input.logicalName().equals("age")
                && input.inputType().equals("number")));
        assertTrue(result.links().stream().anyMatch(link -> link.href().equals("/user/list.html")));
        assertTrue(result.clientRequests().isEmpty());
    }

    @Test
    void extractsSelectAndTextareaControls() throws IOException {
        write("src/main/webapp/user/controls.html", """
                <form action="/user/save.do">
                  <select name="status"></select>
                  <textarea name="memo"></textarea>
                </form>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/controls.html")));

        assertEquals(List.of("status", "memo"), result.inputs().stream().map(HtmlInputInfo::logicalName).toList());
        assertEquals(List.of("select", "textarea"), result.inputs().stream().map(HtmlInputInfo::inputType).toList());
    }

    @Test
    void extractsUnquotedHtmlAttributes() throws IOException {
        write("src/main/webapp/user/unquoted.html", """
                <form action=/user/save.do method=post>
                  <input name=userId type=hidden>
                </form>
                <a href=/user/list.html>Users</a>
                <script src=/static/app.js></script>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/unquoted.html")));

        assertTrue(result.forms().stream().anyMatch(form -> form.action().equals("/user/save.do")
                && form.method().equals("post")));
        assertTrue(result.inputs().stream().anyMatch(input -> input.logicalName().equals("userId")
                && input.inputType().equals("hidden")));
        assertTrue(result.links().stream().anyMatch(link -> link.href().equals("/user/list.html")));
        assertTrue(result.scripts().stream().anyMatch(script -> script.src().equals("/static/app.js")));
    }

    @Test
    void extractsCaseInsensitiveHtmlAttributes() throws IOException {
        write("src/main/webapp/user/case.html", """
                <FORM ACTION="/user/save.do" METHOD="post">
                  <INPUT NAME="userId" TYPE="hidden">
                </FORM>
                <A HREF="/user/list.html">Users</A>
                <SCRIPT SRC="/static/app.js"></SCRIPT>
                """);

        HtmlClientAnalysisResult result = HtmlClientAnalyzer.defaults().analyze(
                tempDir.resolve("src/main/webapp"),
                List.of(tempDir.resolve("src/main/webapp/user/case.html")));

        assertTrue(result.forms().stream().anyMatch(form -> form.action().equals("/user/save.do")
                && form.method().equals("post")));
        assertTrue(result.inputs().stream().anyMatch(input -> input.logicalName().equals("userId")
                && input.inputType().equals("hidden")));
        assertTrue(result.links().stream().anyMatch(link -> link.href().equals("/user/list.html")));
        assertTrue(result.scripts().stream().anyMatch(script -> script.src().equals("/static/app.js")));
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
