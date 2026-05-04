package org.sainm.codeatlas.analyzers.source;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HtmlClientAnalyzer {
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "([\\w:-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'=<>`]+))");
    private static final String TAG_ATTRIBUTES = "((?:\"[^\"]*\"|'[^']*'|[^'\"<>])*)";
    private static final String TAG_NAME_END = "(?=[\\s>/])";
    private static final Pattern FORM = Pattern.compile("<form" + TAG_NAME_END + TAG_ATTRIBUTES + ">", Pattern.CASE_INSENSITIVE);
    private static final Pattern INPUT = Pattern.compile("<input" + TAG_NAME_END + TAG_ATTRIBUTES + ">", Pattern.CASE_INSENSITIVE);
    private static final Pattern SELECT = Pattern.compile("<select" + TAG_NAME_END + TAG_ATTRIBUTES + ">", Pattern.CASE_INSENSITIVE);
    private static final Pattern TEXTAREA = Pattern.compile("<textarea" + TAG_NAME_END + TAG_ATTRIBUTES + ">", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINK = Pattern.compile("<a" + TAG_NAME_END + TAG_ATTRIBUTES + ">", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG_OPEN = Pattern.compile("<([A-Za-z][\\w:-]*)\\b" + TAG_ATTRIBUTES + ">");
    private static final Pattern SCRIPT_SRC = Pattern.compile("<script" + TAG_NAME_END + TAG_ATTRIBUTES + ">", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCRIPT_CLOSE = Pattern.compile("</script\\s*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern EVENT_HANDLER = Pattern.compile("(?<![\\w:-])(on(?:click|submit))\\s*=\\s*(\"([^\"]*)\"|'([^']*)')", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORM_CLOSE = Pattern.compile("</form\\s*>", Pattern.CASE_INSENSITIVE);

    private HtmlClientAnalyzer() {
    }

    public static HtmlClientAnalyzer defaults() {
        return new HtmlClientAnalyzer();
    }

    public HtmlClientAnalysisResult analyze(Path webRoot, List<Path> htmlFiles) {
        if (webRoot == null) {
            throw new IllegalArgumentException("webRoot is required");
        }
        if (htmlFiles == null || htmlFiles.isEmpty()) {
            return new HtmlClientAnalysisResult(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
        List<HtmlPageInfo> pages = new ArrayList<>();
        List<HtmlFormInfo> forms = new ArrayList<>();
        List<HtmlInputInfo> inputs = new ArrayList<>();
        List<HtmlLinkInfo> links = new ArrayList<>();
        List<ScriptResourceInfo> scripts = new ArrayList<>();
        List<ClientRequestInfo> clientRequests = new ArrayList<>();
        List<DomEventHandlerInfo> domEventHandlers = new ArrayList<>();
        List<JavaAnalysisDiagnostic> diagnostics = new ArrayList<>();
        for (Path htmlFile : htmlFiles) {
            try {
                parseFile(webRoot, htmlFile, Files.readString(htmlFile, StandardCharsets.UTF_8),
                        pages, forms, inputs, links, scripts, clientRequests, domEventHandlers);
            } catch (IOException exception) {
                diagnostics.add(new JavaAnalysisDiagnostic("HTML_READ_FAILED", htmlFile + ": " + exception.getMessage()));
            }
        }
        return new HtmlClientAnalysisResult(pages, forms, inputs, links, scripts, clientRequests, domEventHandlers, diagnostics);
    }

    private static void parseFile(
            Path webRoot,
            Path htmlFile,
            String content,
            List<HtmlPageInfo> pages,
            List<HtmlFormInfo> forms,
            List<HtmlInputInfo> inputs,
            List<HtmlLinkInfo> links,
            List<ScriptResourceInfo> scripts,
            List<ClientRequestInfo> clientRequests,
            List<DomEventHandlerInfo> domEventHandlers) {
        String pagePath = relativePath(webRoot, htmlFile);
        pages.add(new HtmlPageInfo(pagePath, new SourceLocation(pagePath, 1, 1)));
        String[] lines = content.split("\\R", -1);
        String currentFormKey = "";
        boolean standaloneJavaScript = isJavaScriptFile(pagePath);
        ScriptState scriptState = standaloneJavaScript ? ScriptState.EXECUTABLE : ScriptState.OUTSIDE;
        boolean htmlComment = false;
        boolean javaScriptBlockComment = false;
        String pendingFetchText = "";
        SourceLocation pendingFetchLocation = null;
        MarkupBuffer markupBuffer = new MarkupBuffer("", null);
        for (int index = 0; index < lines.length; index++) {
            SourceLocation location = new SourceLocation(pagePath, index + 1, 1);
            String line = lines[index];
            if (standaloneJavaScript) {
                SourceLocation requestLocation = pendingFetchLocation == null ? location : pendingFetchLocation;
                String requestText = pendingFetchText.isEmpty()
                        ? line
                        : pendingFetchText + "\n" + line;
                FetchScan fetchScan = addClientRequests(
                        pagePath,
                        requestText,
                        requestLocation,
                        clientRequests,
                        javaScriptBlockComment);
                javaScriptBlockComment = fetchScan.blockComment();
                pendingFetchText = fetchScan.remainder();
                pendingFetchLocation = pendingFetchText.isEmpty() ? null : requestLocation;
                continue;
            }
            ScriptState lineStartScriptState = scriptState;
            MaskScan commentScan = htmlTextOutsideComments(line, lineStartScriptState, htmlComment);
            htmlComment = commentScan.open();
            String uncommentedLine = commentScan.text();
            MarkupLine markupLine = lineStartScriptState == ScriptState.OUTSIDE
                    ? bufferedMarkupLine(uncommentedLine, location, markupBuffer)
                    : new MarkupLine(uncommentedLine, location, new MarkupBuffer("", null));
            markupBuffer = markupLine.buffer();
            String scanLine = markupLine.text();
            SourceLocation scanLocation = markupLine.location();
            String htmlLine = htmlTextOutsideScripts(scanLine, lineStartScriptState);
            currentFormKey = addFormsAndInputs(pagePath, htmlLine, scanLocation, forms, inputs, currentFormKey);
            addLinks(pagePath, htmlLine, scanLocation, links);
            addScripts(pagePath, htmlLine, scanLocation, scripts);
            ScriptScan scriptScan = scriptScan(scanLine, scriptState, standaloneJavaScript);
            SourceLocation requestLocation = pendingFetchLocation == null ? scanLocation : pendingFetchLocation;
            String requestText = pendingFetchText.isEmpty()
                    ? scriptScan.scriptText()
                    : pendingFetchText + "\n" + scriptScan.scriptText();
            FetchScan fetchScan = addClientRequests(
                    pagePath,
                    requestText,
                    requestLocation,
                    clientRequests,
                    javaScriptBlockComment);
            javaScriptBlockComment = fetchScan.blockComment();
            pendingFetchText = fetchScan.remainder();
            pendingFetchLocation = pendingFetchText.isEmpty() ? null : requestLocation;
            scriptState = scriptScan.scriptState();
            if (scriptState != ScriptState.EXECUTABLE) {
                javaScriptBlockComment = false;
                pendingFetchText = "";
                pendingFetchLocation = null;
            }
            addDomEventHandlers(pagePath, scanLine, scanLocation, domEventHandlers, lineStartScriptState);
        }
    }

    private static boolean isJavaScriptFile(String pagePath) {
        return pagePath != null && pagePath.toLowerCase(Locale.ROOT).endsWith(".js");
    }

    private static MaskScan htmlTextOutsideComments(String line, ScriptState scriptState, boolean openComment) {
        String source = line == null ? "" : line;
        StringBuilder result = new StringBuilder(source);
        int index = 0;
        boolean comment = openComment;
        ScriptState state = scriptState;
        while (index < source.length()) {
            if (comment) {
                int close = source.indexOf("-->", index);
                if (close < 0) {
                    maskRange(result, index, source.length());
                    return new MaskScan(result.toString(), true);
                }
                maskRange(result, index, close + "-->".length());
                index = close + "-->".length();
                comment = false;
                continue;
            }
            if (state != ScriptState.OUTSIDE) {
                Matcher closeMatcher = SCRIPT_CLOSE.matcher(source);
                closeMatcher.region(index, source.length());
                if (!closeMatcher.find()) {
                    break;
                }
                index = closeMatcher.end();
                state = ScriptState.OUTSIDE;
                continue;
            }
            int commentStart = source.indexOf("<!--", index);
            Matcher scriptMatcher = SCRIPT_SRC.matcher(source);
            scriptMatcher.region(index, source.length());
            boolean scriptFound = scriptMatcher.find();
            if (commentStart < 0) {
                break;
            }
            if (scriptFound && scriptMatcher.start() < commentStart) {
                Matcher closeMatcher = SCRIPT_CLOSE.matcher(source);
                closeMatcher.region(scriptMatcher.end(), source.length());
                if (!closeMatcher.find()) {
                    break;
                }
                index = closeMatcher.end();
                continue;
            }
            int close = source.indexOf("-->", commentStart + "<!--".length());
            if (close < 0) {
                maskRange(result, commentStart, source.length());
                return new MaskScan(result.toString(), true);
            }
            maskRange(result, commentStart, close + "-->".length());
            index = close + "-->".length();
        }
        return new MaskScan(result.toString(), false);
    }

    private static String htmlTextOutsideScripts(String line, ScriptState scriptState) {
        String source = line == null ? "" : line;
        StringBuilder result = new StringBuilder(source);
        int index = 0;
        ScriptState state = scriptState;
        while (index < source.length()) {
            if (state != ScriptState.OUTSIDE) {
                Matcher closeMatcher = SCRIPT_CLOSE.matcher(source);
                closeMatcher.region(index, source.length());
                if (!closeMatcher.find()) {
                    maskRange(result, index, source.length());
                    break;
                }
                maskRange(result, index, closeMatcher.start());
                index = closeMatcher.end();
                state = ScriptState.OUTSIDE;
                continue;
            }
            Matcher openMatcher = SCRIPT_SRC.matcher(source);
            openMatcher.region(index, source.length());
            if (!openMatcher.find()) {
                break;
            }
            int bodyStart = openMatcher.end();
            Matcher closeMatcher = SCRIPT_CLOSE.matcher(source);
            closeMatcher.region(bodyStart, source.length());
            if (!closeMatcher.find()) {
                maskRange(result, bodyStart, source.length());
                break;
            }
            maskRange(result, bodyStart, closeMatcher.start());
            index = closeMatcher.end();
        }
        return result.toString();
    }

    private static void maskRange(StringBuilder builder, int start, int end) {
        for (int index = start; index < end; index++) {
            builder.setCharAt(index, ' ');
        }
    }

    private static MarkupLine bufferedMarkupLine(String line, SourceLocation location, MarkupBuffer pending) {
        String source = line == null ? "" : line;
        SourceLocation sourceLocation = location;
        if (pending != null && !pending.text().isBlank()) {
            source = pending.text() + "\n" + source;
            sourceLocation = pending.location();
        }
        int pendingStart = unclosedMarkupStart(source);
        if (pendingStart >= 0) {
            SourceLocation pendingLocation = pending != null && !pending.text().isBlank()
                    ? pending.location()
                    : new SourceLocation(location.relativePath(), location.line(), pendingStart);
            return new MarkupLine(
                    source.substring(0, pendingStart),
                    sourceLocation,
                    new MarkupBuffer(source.substring(pendingStart), pendingLocation));
        }
        return new MarkupLine(source, sourceLocation, new MarkupBuffer("", null));
    }

    private static int unclosedMarkupStart(String source) {
        int tagStart = -1;
        char quote = 0;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            if (tagStart < 0) {
                if (isPotentialMarkupStart(source, index)) {
                    tagStart = index;
                }
                continue;
            }
            if (quote != 0) {
                if (current == quote) {
                    quote = 0;
                }
                continue;
            }
            if (current == '"' || current == '\'') {
                quote = current;
                continue;
            }
            if (current == '>') {
                tagStart = -1;
                continue;
            }
            if (isPotentialMarkupStart(source, index)) {
                tagStart = index;
            }
        }
        return tagStart;
    }

    private static boolean isPotentialMarkupStart(String source, int index) {
        if (source.charAt(index) != '<' || index + 1 >= source.length()) {
            return false;
        }
        char next = source.charAt(index + 1);
        return Character.isLetter(next) || next == '/' || next == '!' || next == '%';
    }

    private static String addFormsAndInputs(
            String pagePath,
            String line,
            SourceLocation location,
            List<HtmlFormInfo> forms,
            List<HtmlInputInfo> inputs,
            String currentFormKey) {
        List<HtmlLineEvent> events = new ArrayList<>();
        Matcher formMatcher = FORM.matcher(line);
        while (formMatcher.find()) {
            Map<String, String> attributes = attributes(formMatcher.group(1));
            SourceLocation formLocation = new SourceLocation(
                    location.relativePath(),
                    location.line(),
                    formMatcher.start());
            HtmlFormInfo form = new HtmlFormInfo(
                    pagePath,
                    attributes.getOrDefault("action", ""),
                    attributes.getOrDefault("method", "get"),
                    formLocation);
            events.add(new HtmlLineEvent(formMatcher.start(), 0, form, null, false));
        }
        addInputEvents(pagePath, line, location, events, INPUT, "text", "type");
        addInputEvents(pagePath, line, location, events, SELECT, "select", "");
        addInputEvents(pagePath, line, location, events, TEXTAREA, "textarea", "");
        Matcher closeMatcher = FORM_CLOSE.matcher(line);
        while (closeMatcher.find()) {
            events.add(new HtmlLineEvent(closeMatcher.start(), 2, null, null, true));
        }
        events.sort((left, right) -> left.start == right.start
                ? Integer.compare(left.order, right.order)
                : Integer.compare(left.start, right.start));
        String formKey = currentFormKey;
        for (HtmlLineEvent event : events) {
            if (event.form != null) {
                forms.add(event.form);
                formKey = formKey(event.form);
            } else if (event.input != null) {
                inputs.add(new HtmlInputInfo(
                        event.input.pagePath(),
                        event.input.logicalName(),
                        event.input.inputType(),
                        formKey,
                        event.input.location()));
            } else if (event.closesForm) {
                formKey = "";
            }
        }
        return formKey;
    }

    private static void addInputEvents(
            String pagePath,
            String line,
            SourceLocation location,
            List<HtmlLineEvent> events,
            Pattern pattern,
            String defaultType,
            String typeAttribute) {
        Matcher matcher = pattern.matcher(line);
        while (matcher.find()) {
            Map<String, String> attributes = attributes(matcher.group(1));
            String name = attributes.getOrDefault("name", "");
            if (!name.isBlank()) {
                String inputType = typeAttribute.isBlank()
                        ? defaultType
                        : attributes.getOrDefault(typeAttribute, defaultType);
                HtmlInputInfo input = new HtmlInputInfo(pagePath, name, inputType, "", location);
                events.add(new HtmlLineEvent(matcher.start(), 1, null, input, false));
            }
        }
    }

    private static void addLinks(String pagePath, String line, SourceLocation location, List<HtmlLinkInfo> links) {
        Matcher matcher = LINK.matcher(line);
        while (matcher.find()) {
            String href = attributes(matcher.group(1)).getOrDefault("href", "");
            if (!href.isBlank()) {
                links.add(new HtmlLinkInfo(pagePath, href, location));
            }
        }
    }

    private static void addScripts(String pagePath, String line, SourceLocation location, List<ScriptResourceInfo> scripts) {
        Matcher matcher = SCRIPT_SRC.matcher(line);
        while (matcher.find()) {
            String src = attributes(matcher.group(1)).getOrDefault("src", "");
            if (!src.isBlank()) {
                scripts.add(new ScriptResourceInfo(pagePath, src, location));
            }
        }
    }

    private static ScriptScan scriptScan(String line, ScriptState scriptState, boolean standaloneJavaScript) {
        String source = line == null ? "" : line;
        if (standaloneJavaScript) {
            return new ScriptScan(source, ScriptState.EXECUTABLE);
        }
        StringBuilder scriptText = new StringBuilder();
        int index = 0;
        ScriptState state = scriptState;
        while (index < source.length()) {
            if (state == ScriptState.EXECUTABLE) {
                Matcher closeMatcher = SCRIPT_CLOSE.matcher(source);
                closeMatcher.region(index, source.length());
                if (closeMatcher.find()) {
                    appendScriptText(scriptText, source.substring(index, closeMatcher.start()));
                    index = closeMatcher.end();
                    state = ScriptState.OUTSIDE;
                } else {
                    appendScriptText(scriptText, source.substring(index));
                    index = source.length();
                }
            } else if (state == ScriptState.INERT) {
                Matcher closeMatcher = SCRIPT_CLOSE.matcher(source);
                closeMatcher.region(index, source.length());
                if (closeMatcher.find()) {
                    index = closeMatcher.end();
                    state = ScriptState.OUTSIDE;
                } else {
                    index = source.length();
                }
            } else {
                Matcher openMatcher = SCRIPT_SRC.matcher(source);
                openMatcher.region(index, source.length());
                if (!openMatcher.find()) {
                    break;
                }
                state = isExecutableScript(attributes(openMatcher.group(1)))
                        ? ScriptState.EXECUTABLE
                        : ScriptState.INERT;
                index = openMatcher.end();
            }
        }
        return new ScriptScan(scriptText.toString(), state);
    }

    private static void appendScriptText(StringBuilder scriptText, String segment) {
        if (segment.isBlank()) {
            return;
        }
        if (scriptText.length() > 0) {
            scriptText.append('\n');
        }
        scriptText.append(segment);
    }

    private static boolean isExecutableScript(Map<String, String> attributes) {
        if (!attributes.getOrDefault("src", "").isBlank()) {
            return false;
        }
        String type = attributes.getOrDefault("type", "").trim().toLowerCase(Locale.ROOT);
        int parameters = type.indexOf(';');
        if (parameters >= 0) {
            type = type.substring(0, parameters).trim();
        }
        return type.isBlank()
                || type.equals("module")
                || type.equals("text/javascript")
                || type.equals("application/javascript")
                || type.equals("text/ecmascript")
                || type.equals("application/ecmascript");
    }

    private static void addClientRequests(
            String pagePath,
            String line,
            SourceLocation location,
            List<ClientRequestInfo> clientRequests) {
        for (FetchCall fetch : fetchCalls(line)) {
            clientRequests.add(new ClientRequestInfo(pagePath, fetch.httpMethod(), fetch.url(), location));
        }
    }

    private static FetchScan addClientRequests(
            String pagePath,
            String line,
            SourceLocation location,
            List<ClientRequestInfo> clientRequests,
            boolean blockComment) {
        FetchScan scan = fetchScan(line, blockComment);
        for (FetchCall fetch : scan.calls()) {
            clientRequests.add(new ClientRequestInfo(pagePath, fetch.httpMethod(), fetch.url(), location));
        }
        return scan;
    }

    private static void addDomEventHandlers(
            String pagePath,
            String line,
            SourceLocation location,
            List<DomEventHandlerInfo> domEventHandlers,
            ScriptState scriptState) {
        String source = line == null ? "" : line;
        int index = 0;
        ScriptState state = scriptState;
        while (index < source.length()) {
            if (state != ScriptState.OUTSIDE) {
                Matcher closeMatcher = SCRIPT_CLOSE.matcher(source);
                closeMatcher.region(index, source.length());
                if (!closeMatcher.find()) {
                    break;
                }
                index = closeMatcher.end();
                state = ScriptState.OUTSIDE;
                continue;
            }
            Matcher tagMatcher = TAG_OPEN.matcher(source);
            tagMatcher.region(index, source.length());
            if (!tagMatcher.find()) {
                break;
            }
            String tagName = tagMatcher.group(1).toLowerCase(Locale.ROOT);
            if (tagName.equals("script")) {
                state = isExecutableScript(attributes(tagMatcher.group(2)))
                        ? ScriptState.EXECUTABLE
                        : ScriptState.INERT;
            } else {
                addDomEventHandlersInAttributes(
                        pagePath,
                        tagMatcher.group(2),
                        tagMatcher.start(2),
                        location,
                        domEventHandlers);
            }
            index = tagMatcher.end();
        }
    }

    private static void addDomEventHandlersInAttributes(
            String pagePath,
            String attributes,
            int attributesStart,
            SourceLocation location,
            List<DomEventHandlerInfo> domEventHandlers) {
        Matcher matcher = EVENT_HANDLER.matcher(attributes);
        while (matcher.find()) {
            String eventName = matcher.group(1).toLowerCase();
            String handler = matcher.group(3) != null ? matcher.group(3) : matcher.group(4);
            SourceLocation handlerLocation = new SourceLocation(
                    location.relativePath(),
                    location.line(),
                    attributesStart + matcher.start(1));
            for (String navigation : navigationTargets(handler)) {
                domEventHandlers.add(new DomEventHandlerInfo(
                        pagePath,
                        eventName,
                        "NAVIGATES_TO",
                        navigation,
                        "GET",
                        handlerLocation));
            }
            for (FetchCall fetch : fetchCalls(handler)) {
                domEventHandlers.add(new DomEventHandlerInfo(
                        pagePath,
                        eventName,
                        "CALLS_HTTP",
                        fetch.url(),
                        fetch.httpMethod(),
                        handlerLocation));
            }
        }
    }

    private static List<FetchCall> fetchCalls(String text) {
        return fetchScan(text, false).calls();
    }

    private static List<String> navigationTargets(String text) {
        List<String> result = new ArrayList<>();
        String source = text == null ? "" : text;
        int index = 0;
        char quote = 0;
        boolean escaped = false;
        boolean lineComment = false;
        boolean blockComment = false;
        while (index < source.length()) {
            char current = source.charAt(index);
            if (lineComment) {
                if (current == '\n' || current == '\r') {
                    lineComment = false;
                }
                index++;
                continue;
            }
            if (blockComment) {
                if (current == '*' && index + 1 < source.length() && source.charAt(index + 1) == '/') {
                    blockComment = false;
                    index += 2;
                } else {
                    index++;
                }
                continue;
            }
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = 0;
                }
                index++;
                continue;
            }
            if (current == '/' && index + 1 < source.length()) {
                char next = source.charAt(index + 1);
                if (next == '/') {
                    lineComment = true;
                    index += 2;
                    continue;
                }
                if (next == '*') {
                    blockComment = true;
                    index += 2;
                    continue;
                }
            }
            if (isJavaScriptStringQuote(current)) {
                quote = current;
                index++;
                continue;
            }
            if (!source.startsWith("location.href", index)) {
                index++;
                continue;
            }
            int afterName = index + "location.href".length();
            if (!isBrowserLocationHref(source, index, afterName)) {
                index = afterName;
                continue;
            }
            int assignment = skipJavaScriptWhitespaceAndComments(source, afterName);
            if (assignment >= source.length() || source.charAt(assignment) != '=') {
                index = afterName;
                continue;
            }
            int targetStart = skipJavaScriptWhitespaceAndComments(source, assignment + 1);
            if (targetStart >= source.length() || !isStaticJavaScriptLiteralStart(source.charAt(targetStart))) {
                index = assignment + 1;
                continue;
            }
            int targetEnd = stringEnd(source, targetStart);
            if (targetEnd < 0) {
                break;
            }
            String target = staticJavaScriptLiteralValue(source, targetStart, targetEnd);
            if (target != null && isStaticJavaScriptExpressionEnd(source, targetEnd + 1)) {
                result.add(target.isEmpty() ? "?" : target);
            }
            index = targetEnd + 1;
        }
        return result;
    }

    private static FetchScan fetchScan(String text, boolean initialBlockComment) {
        List<FetchCall> result = new ArrayList<>();
        String source = text == null ? "" : text;
        int index = 0;
        char quote = 0;
        boolean escaped = false;
        boolean lineComment = false;
        boolean blockComment = initialBlockComment;
        while (index < source.length()) {
            char current = source.charAt(index);
            if (lineComment) {
                if (current == '\n' || current == '\r') {
                    lineComment = false;
                }
                index++;
                continue;
            }
            if (blockComment) {
                if (current == '*' && index + 1 < source.length() && source.charAt(index + 1) == '/') {
                    blockComment = false;
                    index += 2;
                } else {
                    index++;
                }
                continue;
            }
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = 0;
                }
                index++;
                continue;
            }
            if (current == '/' && index + 1 < source.length()) {
                char next = source.charAt(index + 1);
                if (next == '/') {
                    lineComment = true;
                    index += 2;
                    continue;
                }
                if (next == '*') {
                    blockComment = true;
                    index += 2;
                    continue;
                }
            }
            if (isJavaScriptStringQuote(current)) {
                quote = current;
                index++;
                continue;
            }
            if (!source.startsWith("fetch", index)) {
                index++;
                continue;
            }
            int fetchStart = index;
            int afterName = fetchStart + "fetch".length();
            if (!isGlobalFetchCall(source, fetchStart, afterName)) {
                index = afterName;
                continue;
            }
            int open = skipJavaScriptWhitespaceAndComments(source, afterName);
            if (open >= source.length()) {
                return new FetchScan(result, blockComment, source.substring(fetchStart));
            }
            if (source.charAt(open) != '(') {
                index = afterName;
                continue;
            }
            int argumentStart = skipJavaScriptWhitespaceAndComments(source, open + 1);
            if (argumentStart >= source.length()) {
                return new FetchScan(result, blockComment, source.substring(fetchStart));
            }
            if (!isStaticJavaScriptLiteralStart(source.charAt(argumentStart))) {
                index = open + 1;
                continue;
            }
            int urlEnd = stringEnd(source, argumentStart);
            if (urlEnd < 0) {
                return new FetchScan(result, blockComment, source.substring(fetchStart));
            }
            String url = staticJavaScriptLiteralValue(source, argumentStart, urlEnd);
            if (url == null) {
                index = urlEnd + 1;
                continue;
            }
            if (url.isEmpty()) {
                url = "?";
            }
            int close = matchingParen(source, open);
            if (close < 0) {
                return new FetchScan(result, blockComment, source.substring(fetchStart));
            }
            int afterUrl = skipJavaScriptWhitespaceAndComments(source, urlEnd + 1);
            if (afterUrl < close && source.charAt(afterUrl) != ',') {
                index = close + 1;
                continue;
            }
            String options = afterUrl < close ? source.substring(afterUrl + 1, close) : "";
            String httpMethod = httpMethod(options);
            if (httpMethod != null) {
                result.add(new FetchCall(url, httpMethod));
            }
            index = close + 1;
        }
        return new FetchScan(result, blockComment, "");
    }

    private static String httpMethod(String options) {
        String source = options == null ? "" : options;
        int index = skipJavaScriptWhitespaceAndComments(source, 0);
        if (index >= source.length()) {
            return "GET";
        }
        if (source.charAt(index) != '{') {
            return null;
        }
        String detectedMethod = null;
        int objectDepth = 0;
        boolean lineComment = false;
        boolean blockComment = false;
        while (index < source.length()) {
            char current = source.charAt(index);
            if (lineComment) {
                if (current == '\n' || current == '\r') {
                    lineComment = false;
                }
                index++;
                continue;
            }
            if (blockComment) {
                if (current == '*' && index + 1 < source.length() && source.charAt(index + 1) == '/') {
                    blockComment = false;
                    index += 2;
                } else {
                    index++;
                }
                continue;
            }
            if (current == '/' && index + 1 < source.length()) {
                char next = source.charAt(index + 1);
                if (next == '/') {
                    lineComment = true;
                    index += 2;
                    continue;
                }
                if (next == '*') {
                    blockComment = true;
                    index += 2;
                    continue;
                }
            }
            if (isStaticJavaScriptLiteralStart(current)) {
                int end = stringEnd(source, index);
                if (end < 0) {
                    return null;
                }
                if (isQuote(current)) {
                    int colon = skipJavaScriptWhitespaceAndComments(source, end + 1);
                    if (objectDepth == 1
                            && source.substring(index + 1, end).equalsIgnoreCase("method")
                            && colon < source.length()
                            && source.charAt(colon) == ':') {
                        detectedMethod = methodValue(source, colon + 1);
                        if (detectedMethod == null) {
                            return null;
                        }
                        index = colon + 1;
                        continue;
                    }
                }
                index = end + 1;
                continue;
            }
            if (objectDepth == 1 && current == '.' && source.startsWith("...", index)) {
                return null;
            }
            if (current == '{') {
                objectDepth++;
                index++;
                continue;
            }
            if (current == '}') {
                objectDepth = Math.max(0, objectDepth - 1);
                index++;
                continue;
            }
            if (isJavaScriptIdentifierStart(current)) {
                int end = index + 1;
                while (end < source.length() && isJavaScriptIdentifierPart(source.charAt(end))) {
                    end++;
                }
                if (objectDepth == 1 && source.substring(index, end).equalsIgnoreCase("method")) {
                    int colon = skipJavaScriptWhitespaceAndComments(source, end);
                    if (colon < source.length() && source.charAt(colon) == ':') {
                        detectedMethod = methodValue(source, colon + 1);
                        if (detectedMethod == null) {
                            return null;
                        }
                        index = colon + 1;
                        continue;
                    }
                    return null;
                }
                index = end;
                continue;
            }
            index++;
        }
        return detectedMethod == null ? "GET" : detectedMethod;
    }

    private static String methodValue(String source, int index) {
        int valueStart = skipJavaScriptWhitespaceAndComments(source, index);
        if (valueStart >= source.length() || !isStaticJavaScriptLiteralStart(source.charAt(valueStart))) {
            return null;
        }
        int valueEnd = stringEnd(source, valueStart);
        if (valueEnd < 0) {
            return null;
        }
        String value = staticJavaScriptLiteralValue(source, valueStart, valueEnd);
        if (value == null) {
            return null;
        }
        int valueAfter = skipJavaScriptWhitespaceAndComments(source, valueEnd + 1);
        if (valueAfter < source.length() && source.charAt(valueAfter) != ',' && source.charAt(valueAfter) != '}') {
            return null;
        }
        return value.toUpperCase(Locale.ROOT);
    }

    private static boolean isGlobalFetchCall(String source, int start, int end) {
        if (end < source.length() && isJavaScriptIdentifierPart(source.charAt(end))) {
            return false;
        }
        String qualifier = memberQualifier(source, start);
        if (qualifier != null) {
            return qualifier.equals("window")
                    || qualifier.equals("self")
                    || qualifier.equals("globalThis");
        }
        return start == 0 || !isJavaScriptIdentifierPart(source.charAt(start - 1));
    }

    private static boolean isBrowserLocationHref(String source, int start, int end) {
        if (end < source.length() && isJavaScriptIdentifierPart(source.charAt(end))) {
            return false;
        }
        String qualifier = memberQualifier(source, start);
        if (qualifier != null) {
            return qualifier.equals("window")
                    || qualifier.equals("self")
                    || qualifier.equals("globalThis")
                    || qualifier.equals("document");
        }
        return start == 0 || !isJavaScriptIdentifierPart(source.charAt(start - 1));
    }

    private static String memberQualifier(String source, int memberStart) {
        int dot = skipWhitespaceBackward(source, memberStart - 1);
        if (dot < 0 || source.charAt(dot) != '.') {
            return null;
        }
        int qualifierEnd = skipWhitespaceBackward(source, dot - 1);
        if (qualifierEnd < 0 || !isJavaScriptIdentifierPart(source.charAt(qualifierEnd))) {
            return "";
        }
        int qualifierStart = qualifierEnd;
        while (qualifierStart >= 0 && isJavaScriptIdentifierPart(source.charAt(qualifierStart))) {
            qualifierStart--;
        }
        int beforeQualifier = skipWhitespaceBackward(source, qualifierStart);
        if (beforeQualifier >= 0
                && (isJavaScriptIdentifierPart(source.charAt(beforeQualifier)) || source.charAt(beforeQualifier) == '.')) {
            return "";
        }
        return source.substring(qualifierStart + 1, qualifierEnd + 1);
    }

    private static boolean isJavaScriptIdentifierStart(char value) {
        return Character.isLetter(value) || value == '_' || value == '$';
    }

    private static boolean isJavaScriptIdentifierPart(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '$';
    }

    private static int skipWhitespace(String source, int index) {
        int result = index;
        while (result < source.length() && Character.isWhitespace(source.charAt(result))) {
            result++;
        }
        return result;
    }

    private static int skipWhitespaceBackward(String source, int index) {
        int result = index;
        while (result >= 0 && Character.isWhitespace(source.charAt(result))) {
            result--;
        }
        return result;
    }

    private static int skipJavaScriptWhitespaceAndComments(String source, int index) {
        int result = index;
        while (result < source.length()) {
            if (Character.isWhitespace(source.charAt(result))) {
                result++;
                continue;
            }
            if (source.charAt(result) == '/' && result + 1 < source.length()) {
                char next = source.charAt(result + 1);
                if (next == '/') {
                    result += 2;
                    while (result < source.length()
                            && source.charAt(result) != '\n'
                            && source.charAt(result) != '\r') {
                        result++;
                    }
                    continue;
                }
                if (next == '*') {
                    int close = source.indexOf("*/", result + 2);
                    if (close < 0) {
                        return source.length();
                    }
                    result = close + 2;
                    continue;
                }
            }
            break;
        }
        return result;
    }

    private static boolean isStaticJavaScriptExpressionEnd(String source, int index) {
        int end = skipJavaScriptWhitespaceAndComments(source, index);
        return end >= source.length() || source.charAt(end) == ';' || source.charAt(end) == '}'
                || source.charAt(end) == ')' || source.charAt(end) == ',';
    }

    private static boolean isQuote(char value) {
        return value == '\'' || value == '"';
    }

    private static boolean isJavaScriptStringQuote(char value) {
        return isStaticJavaScriptLiteralStart(value);
    }

    private static boolean isStaticJavaScriptLiteralStart(char value) {
        return isQuote(value) || value == '`';
    }

    private static String staticJavaScriptLiteralValue(String source, int start, int end) {
        if (source.charAt(start) == '`' && containsTemplateInterpolation(source, start, end)) {
            return null;
        }
        return source.substring(start + 1, end);
    }

    private static boolean containsTemplateInterpolation(String source, int start, int end) {
        boolean escaped = false;
        for (int index = start + 1; index < end; index++) {
            char current = source.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '$' && index + 1 < end && source.charAt(index + 1) == '{') {
                return true;
            }
        }
        return false;
    }

    private static int stringEnd(String source, int quoteIndex) {
        char quote = source.charAt(quoteIndex);
        boolean escaped = false;
        for (int index = quoteIndex + 1; index < source.length(); index++) {
            char current = source.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == quote) {
                return index;
            }
        }
        return -1;
    }

    private static int matchingParen(String source, int open) {
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        boolean lineComment = false;
        boolean blockComment = false;
        for (int index = open; index < source.length(); index++) {
            char current = source.charAt(index);
            if (lineComment) {
                if (current == '\n' || current == '\r') {
                    lineComment = false;
                }
                continue;
            }
            if (blockComment) {
                if (current == '*' && index + 1 < source.length() && source.charAt(index + 1) == '/') {
                    blockComment = false;
                    index++;
                }
                continue;
            }
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = 0;
                }
                continue;
            }
            if (current == '/' && index + 1 < source.length()) {
                char next = source.charAt(index + 1);
                if (next == '/') {
                    lineComment = true;
                    index++;
                    continue;
                }
                if (next == '*') {
                    blockComment = true;
                    index++;
                    continue;
                }
            }
            if (isJavaScriptStringQuote(current)) {
                quote = current;
                continue;
            }
            if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static Map<String, String> attributes(String text) {
        Map<String, String> result = new LinkedHashMap<>();
        Matcher matcher = ATTRIBUTE.matcher(text == null ? "" : text);
        while (matcher.find()) {
            String value = matcher.group(2) != null
                    ? matcher.group(2)
                    : matcher.group(3) != null ? matcher.group(3) : matcher.group(4);
            result.put(matcher.group(1).toLowerCase(Locale.ROOT), value);
        }
        return result;
    }

    private static String relativePath(Path webRoot, Path file) {
        return webRoot.toAbsolutePath().normalize()
                .relativize(file.toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');
    }

    private static String formKey(HtmlFormInfo form) {
        return form.action() + ":" + form.method() + ":" + form.location().line()
                + ":" + form.location().column();
    }

    private record HtmlLineEvent(
            int start,
            int order,
            HtmlFormInfo form,
            HtmlInputInfo input,
            boolean closesForm) {
    }

    private record FetchCall(String url, String httpMethod) {
    }

    private record FetchScan(List<FetchCall> calls, boolean blockComment, String remainder) {
    }

    private record MaskScan(String text, boolean open) {
    }

    private record MarkupBuffer(String text, SourceLocation location) {
    }

    private record MarkupLine(String text, SourceLocation location, MarkupBuffer buffer) {
    }

    private enum ScriptState {
        OUTSIDE,
        EXECUTABLE,
        INERT
    }

    private record ScriptScan(String scriptText, ScriptState scriptState) {
    }
}
