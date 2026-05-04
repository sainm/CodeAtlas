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

public final class JspSemanticAnalyzer {
    public static final String TOMCAT_10_JAKARTA_PROFILE = "TOMCAT_10_JAKARTA";
    public static final String TOMCAT_8_9_JAVAX_PROFILE = "TOMCAT_8_9_JAVAX";

    private static final Pattern DIRECTIVE = Pattern.compile("<%@\\s*([\\w-]+)\\s+([^%]*)%>");
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "([\\w:-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'=<>`]+))");
    private static final String TAG_ATTRIBUTES = "((?:\"[^\"]*\"|'[^']*'|[^'\"<>])*)";
    private static final String TAG_NAME_END = "(?=[\\s>/])";
    private static final Pattern JSP_INCLUDE = Pattern.compile("<jsp:include" + TAG_NAME_END + TAG_ATTRIBUTES + ">");
    private static final Pattern JSP_FORWARD = Pattern.compile("<jsp:forward" + TAG_NAME_END + TAG_ATTRIBUTES + ">");
    private static final Pattern FORM = Pattern.compile("<(html:form|form:form|form)" + TAG_NAME_END + TAG_ATTRIBUTES + ">", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_INPUT = Pattern.compile("<input" + TAG_NAME_END + TAG_ATTRIBUTES + ">", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_SELECT = Pattern.compile("<select" + TAG_NAME_END + TAG_ATTRIBUTES + ">", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_TEXTAREA = Pattern.compile("<textarea" + TAG_NAME_END + TAG_ATTRIBUTES + ">", Pattern.CASE_INSENSITIVE);
    private static final Pattern STRUTS_INPUT = Pattern.compile("<html:(text|hidden|password|select|textarea|checkbox|radio)" + TAG_NAME_END + TAG_ATTRIBUTES + ">");
    private static final Pattern SPRING_INPUT = Pattern.compile("<form:(input|select|textarea|checkbox|password)" + TAG_NAME_END + TAG_ATTRIBUTES + ">");
    private static final Pattern EL_PARAM = Pattern.compile("\\$\\{\\s*param\\.([A-Za-z0-9_.$-]+)");
    private static final Pattern EL_PARAM_BRACKET = Pattern.compile("\\$\\{\\s*param\\s*\\[\\s*(?:\"([^\"]+)\"|'([^']+)')\\s*\\]");
    private static final Pattern GET_PARAMETER = Pattern.compile("request\\.getParameter\\(\\s*\"([^\"]+)\"\\s*\\)");
    private static final Pattern FORM_CLOSE = Pattern.compile("</(?:html:form|form:form|form)\\s*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCRIPT_OPEN = Pattern.compile("<script" + TAG_NAME_END + TAG_ATTRIBUTES + ">", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCRIPT_CLOSE = Pattern.compile("</script\\s*>", Pattern.CASE_INSENSITIVE);

    private final JasperJspPrecompiler defaultPrecompiler;
    private final JasperProfileClassLoaderFactory profileClassLoaderFactory;

    private JspSemanticAnalyzer(
            JasperJspPrecompiler defaultPrecompiler,
            JasperProfileClassLoaderFactory profileClassLoaderFactory) {
        this.defaultPrecompiler = defaultPrecompiler;
        this.profileClassLoaderFactory = profileClassLoaderFactory;
    }

    public static JspSemanticAnalyzer defaults() {
        return new JspSemanticAnalyzer(JasperJspPrecompiler.defaults(), JasperProfileClassLoaderFactory.defaults());
    }

    static JspSemanticAnalyzer using(JasperProfileClassLoaderFactory profileClassLoaderFactory) {
        JasperProfileClassLoaderFactory factory = profileClassLoaderFactory == null
                ? JasperProfileClassLoaderFactory.defaults()
                : profileClassLoaderFactory;
        return new JspSemanticAnalyzer(JasperJspPrecompiler.defaults(), factory);
    }

    public static JspSemanticAnalyzer usingJasperProfileClasspaths(Map<String, List<Path>> classpathByProfile) {
        return new JspSemanticAnalyzer(
                JasperJspPrecompiler.defaults(),
                JasperProfileClassLoaderFactory.usingProfileClasspaths(classpathByProfile));
    }

    public JspAnalysisResult analyze(Path webRoot, List<Path> jspFiles) {
        return analyze(webRoot, null, jspFiles);
    }

    public JspAnalysisResult analyze(Path webRoot, WebAppContext context, List<Path> jspFiles) {
        if (webRoot == null) {
            throw new IllegalArgumentException("webRoot is required");
        }
        if (jspFiles == null || jspFiles.isEmpty()) {
            return emptyResult(JspParserMode.TOKEN_FALLBACK);
        }

        List<JavaAnalysisDiagnostic> diagnostics = new ArrayList<>();
        JspParseAttempt parseAttempt = precompilerFor(context).precompile(webRoot, jspFiles);
        JspParserMode parserMode = parseAttempt.parserMode();
        diagnostics.addAll(parseAttempt.diagnostics());

        List<JspPageInfo> pages = new ArrayList<>();
        List<JspDirectiveInfo> directives = new ArrayList<>();
        List<JspTaglibInfo> taglibs = new ArrayList<>();
        List<JspIncludeInfo> includes = new ArrayList<>();
        List<JspForwardInfo> forwards = new ArrayList<>();
        List<JspFormInfo> forms = new ArrayList<>();
        List<JspInputInfo> inputs = new ArrayList<>();
        List<JspRequestParameterAccessInfo> requestParameters = new ArrayList<>();

        for (Path jspFile : jspFiles) {
            try {
                String content = Files.readString(jspFile, StandardCharsets.UTF_8);
                SourceLocation pageLocation = location(webRoot, jspFile, 1);
                if (!pageLocation.relativePath().isBlank()) {
                    pages.add(new JspPageInfo(pageLocation.relativePath(), pageLocation));
                }
                parseFallback(
                        webRoot,
                        jspFile,
                        content,
                        directives,
                        taglibs,
                        includes,
                        forwards,
                        forms,
                        inputs,
                        requestParameters);
            } catch (IOException exception) {
                diagnostics.add(new JavaAnalysisDiagnostic(
                        "JSP_READ_FAILED",
                        jspFile + ": " + exception.getMessage()));
            }
        }

        return new JspAnalysisResult(
                parserMode,
                pages,
                directives,
                taglibs,
                includes,
                forwards,
                forms,
                inputs,
                requestParameters,
                diagnostics);
    }

    private JasperJspPrecompiler precompilerFor(WebAppContext context) {
        if (context == null) {
            return defaultPrecompiler;
        }
        return JasperJspPrecompiler.using(profileClassLoaderFactory, JasperProjectContext.from(context));
    }

    private static void parseFallback(
            Path webRoot,
            Path jspFile,
            String content,
            List<JspDirectiveInfo> directives,
            List<JspTaglibInfo> taglibs,
            List<JspIncludeInfo> includes,
            List<JspForwardInfo> forwards,
            List<JspFormInfo> forms,
            List<JspInputInfo> inputs,
            List<JspRequestParameterAccessInfo> requestParameters) {
        String[] lines = content.split("\\R", -1);
        String currentFormKey = "";
        boolean jspComment = false;
        boolean jspCode = false;
        boolean javaBlockComment = false;
        boolean htmlComment = false;
        boolean scriptBody = false;
        JspMarkupBuffer markupBuffer = new JspMarkupBuffer("", null);
        for (int index = 0; index < lines.length; index++) {
            int lineNumber = index + 1;
            JspMaskScan commentScan = textOutsideJspComments(lines[index], jspComment);
            jspComment = commentScan.open();
            String line = commentScan.text();
            String markupLine = jspMarkupText(line, jspCode).text();
            JspMaskScan codeScan = jspCodeText(line, jspCode);
            jspCode = codeScan.open();
            JspMaskScan javaCommentScan = javaCodeOutsideComments(codeScan.text(), javaBlockComment);
            javaBlockComment = javaCommentScan.open();
            SourceLocation location = location(webRoot, jspFile, lineNumber);
            JspMarkupLine scanLine = bufferedMarkupLine(markupLine, location, markupBuffer);
            markupBuffer = scanLine.buffer();
            JspMaskScan htmlCommentScan = htmlTextOutsideComments(scanLine.text(), htmlComment);
            htmlComment = htmlCommentScan.open();
            JspMaskScan scriptScan = htmlTextOutsideScripts(htmlCommentScan.text(), scriptBody);
            scriptBody = scriptScan.open();
            String inertMaskedLine = scriptScan.text();
            extractDirectives(inertMaskedLine, scanLine.location(), directives, taglibs, includes);
            extractElementPaths(inertMaskedLine, scanLine.location(), includes, forwards);
            currentFormKey = extractFormsAndInputs(inertMaskedLine, scanLine.location(), forms, inputs, currentFormKey);
            extractRequestAccesses(htmlCommentScan.text(), javaCommentScan.text(), scanLine.location(), requestParameters);
        }
    }

    private static JspMaskScan textOutsideJspComments(String line, boolean openComment) {
        String source = line == null ? "" : line;
        StringBuilder result = new StringBuilder(source);
        int index = 0;
        boolean comment = openComment;
        while (index < source.length()) {
            if (comment) {
                int close = source.indexOf("--%>", index);
                if (close < 0) {
                    maskRange(result, index, source.length());
                    return new JspMaskScan(result.toString(), true);
                }
                maskRange(result, index, close + "--%>".length());
                index = close + "--%>".length();
                comment = false;
                continue;
            }
            int open = source.indexOf("<%--", index);
            if (open < 0) {
                break;
            }
            int close = source.indexOf("--%>", open + "<%--".length());
            if (close < 0) {
                maskRange(result, open, source.length());
                return new JspMaskScan(result.toString(), true);
            }
            maskRange(result, open, close + "--%>".length());
            index = close + "--%>".length();
        }
        return new JspMaskScan(result.toString(), false);
    }

    private static JspMaskScan jspCodeText(String line, boolean openCode) {
        String source = line == null ? "" : line;
        StringBuilder result = new StringBuilder(" ".repeat(source.length()));
        int index = 0;
        boolean code = openCode;
        while (index < source.length()) {
            if (code) {
                int close = source.indexOf("%>", index);
                if (close < 0) {
                    copyRange(source, result, index, source.length());
                    return new JspMaskScan(result.toString(), true);
                }
                copyRange(source, result, index, close);
                index = close + "%>".length();
                code = false;
                continue;
            }
            int open = source.indexOf("<%", index);
            if (open < 0) {
                break;
            }
            if (!isJspCodeStart(source, open)) {
                int close = source.indexOf("%>", open + "<%".length());
                if (close < 0) {
                    break;
                }
                index = close + "%>".length();
                continue;
            }
            index = open + "<%".length();
            if (index < source.length() && (source.charAt(index) == '=' || source.charAt(index) == '!')) {
                index++;
            }
            code = true;
        }
        return new JspMaskScan(result.toString(), code);
    }

    private static JspMaskScan jspMarkupText(String line, boolean openCode) {
        String source = line == null ? "" : line;
        StringBuilder result = new StringBuilder(source);
        int index = 0;
        boolean code = openCode;
        while (index < source.length()) {
            if (code) {
                int close = source.indexOf("%>", index);
                if (close < 0) {
                    maskRange(result, index, source.length());
                    return new JspMaskScan(result.toString(), true);
                }
                maskRange(result, index, close + "%>".length());
                index = close + "%>".length();
                code = false;
                continue;
            }
            int open = source.indexOf("<%", index);
            if (open < 0) {
                break;
            }
            if (!isJspCodeStart(source, open)) {
                int close = source.indexOf("%>", open + "<%".length());
                if (close < 0) {
                    break;
                }
                index = close + "%>".length();
                continue;
            }
            int close = source.indexOf("%>", open + "<%".length());
            if (close < 0) {
                maskRange(result, open, source.length());
                return new JspMaskScan(result.toString(), true);
            }
            maskRange(result, open, close + "%>".length());
            index = close + "%>".length();
        }
        return new JspMaskScan(result.toString(), false);
    }

    private static JspMaskScan javaCodeOutsideComments(String line, boolean openBlockComment) {
        String source = line == null ? "" : line;
        StringBuilder result = new StringBuilder(source);
        int index = 0;
        boolean blockComment = openBlockComment;
        char quote = 0;
        boolean escaped = false;
        while (index < source.length()) {
            if (blockComment) {
                int close = source.indexOf("*/", index);
                if (close < 0) {
                    maskRange(result, index, source.length());
                    return new JspMaskScan(result.toString(), true);
                }
                maskRange(result, index, close + "*/".length());
                index = close + "*/".length();
                blockComment = false;
                continue;
            }
            char current = source.charAt(index);
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
            if (current == '"' || current == '\'') {
                quote = current;
                index++;
                continue;
            }
            if (current == '/' && index + 1 < source.length()) {
                char next = source.charAt(index + 1);
                if (next == '/') {
                    maskRange(result, index, source.length());
                    return new JspMaskScan(result.toString(), false);
                }
                if (next == '*') {
                    int close = source.indexOf("*/", index + 2);
                    if (close < 0) {
                        maskRange(result, index, source.length());
                        return new JspMaskScan(result.toString(), true);
                    }
                    maskRange(result, index, close + "*/".length());
                    index = close + "*/".length();
                    continue;
                }
            }
            index++;
        }
        return new JspMaskScan(result.toString(), false);
    }

    private static boolean isJspCodeStart(String source, int open) {
        int next = open + "<%".length();
        return next >= source.length()
                || (source.charAt(next) != '@' && source.charAt(next) != '-');
    }

    private static JspMaskScan htmlTextOutsideComments(String line, boolean openComment) {
        String source = line == null ? "" : line;
        StringBuilder result = new StringBuilder(source);
        int index = 0;
        boolean comment = openComment;
        while (index < source.length()) {
            if (comment) {
                int close = source.indexOf("-->", index);
                if (close < 0) {
                    maskRange(result, index, source.length());
                    return new JspMaskScan(result.toString(), true);
                }
                maskRange(result, index, close + "-->".length());
                index = close + "-->".length();
                comment = false;
                continue;
            }
            int open = source.indexOf("<!--", index);
            if (open < 0) {
                break;
            }
            int close = source.indexOf("-->", open + "<!--".length());
            if (close < 0) {
                maskRange(result, open, source.length());
                return new JspMaskScan(result.toString(), true);
            }
            maskRange(result, open, close + "-->".length());
            index = close + "-->".length();
        }
        return new JspMaskScan(result.toString(), false);
    }

    private static JspMaskScan htmlTextOutsideScripts(String line, boolean openScript) {
        String source = line == null ? "" : line;
        StringBuilder result = new StringBuilder(source);
        int index = 0;
        boolean script = openScript;
        while (index < source.length()) {
            if (script) {
                Matcher closeMatcher = SCRIPT_CLOSE.matcher(source);
                closeMatcher.region(index, source.length());
                if (!closeMatcher.find()) {
                    maskRange(result, index, source.length());
                    return new JspMaskScan(result.toString(), true);
                }
                maskRange(result, index, closeMatcher.start());
                index = closeMatcher.end();
                script = false;
                continue;
            }
            Matcher openMatcher = SCRIPT_OPEN.matcher(source);
            openMatcher.region(index, source.length());
            if (!openMatcher.find()) {
                break;
            }
            int bodyStart = openMatcher.end();
            Matcher closeMatcher = SCRIPT_CLOSE.matcher(source);
            closeMatcher.region(bodyStart, source.length());
            if (!closeMatcher.find()) {
                maskRange(result, bodyStart, source.length());
                return new JspMaskScan(result.toString(), true);
            }
            maskRange(result, bodyStart, closeMatcher.start());
            index = closeMatcher.end();
        }
        return new JspMaskScan(result.toString(), false);
    }

    private static void maskRange(StringBuilder builder, int start, int end) {
        for (int index = start; index < end; index++) {
            builder.setCharAt(index, ' ');
        }
    }

    private static void copyRange(String source, StringBuilder target, int start, int end) {
        for (int index = start; index < end; index++) {
            target.setCharAt(index, source.charAt(index));
        }
    }

    private static JspMarkupLine bufferedMarkupLine(String line, SourceLocation location, JspMarkupBuffer pending) {
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
            return new JspMarkupLine(
                    source.substring(0, pendingStart),
                    sourceLocation,
                    new JspMarkupBuffer(source.substring(pendingStart), pendingLocation));
        }
        return new JspMarkupLine(source, sourceLocation, new JspMarkupBuffer("", null));
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

    private static void extractDirectives(
            String line,
            SourceLocation location,
            List<JspDirectiveInfo> directives,
            List<JspTaglibInfo> taglibs,
            List<JspIncludeInfo> includes) {
        Matcher matcher = DIRECTIVE.matcher(line);
        while (matcher.find()) {
            String name = matcher.group(1);
            Map<String, String> attributes = attributes(matcher.group(2));
            directives.add(new JspDirectiveInfo(name, attributes, location));
            if (name.equals("taglib")) {
                String prefix = attributes.getOrDefault("prefix", "");
                String uri = attributes.getOrDefault("uri", attributes.getOrDefault("tagdir", ""));
                if (!prefix.isBlank() && !uri.isBlank()) {
                    taglibs.add(new JspTaglibInfo(prefix, uri, location));
                }
            }
            if (name.equals("include")) {
                String file = attributes.getOrDefault("file", "");
                if (!file.isBlank() && !isDynamicTarget(file)) {
                    includes.add(new JspIncludeInfo(file, JspIncludeKind.STATIC, location));
                }
            }
        }
    }

    private static void extractElementPaths(
            String line,
            SourceLocation location,
            List<JspIncludeInfo> includes,
            List<JspForwardInfo> forwards) {
        Matcher includeMatcher = JSP_INCLUDE.matcher(line);
        while (includeMatcher.find()) {
            String page = attributes(includeMatcher.group(1)).getOrDefault("page", "");
            if (!page.isBlank() && !isDynamicTarget(page)) {
                includes.add(new JspIncludeInfo(page, JspIncludeKind.DYNAMIC, location));
            }
        }
        Matcher forwardMatcher = JSP_FORWARD.matcher(line);
        while (forwardMatcher.find()) {
            String page = attributes(forwardMatcher.group(1)).getOrDefault("page", "");
            if (!page.isBlank() && !isDynamicTarget(page)) {
                forwards.add(new JspForwardInfo(page, location));
            }
        }
    }

    private static boolean isDynamicTarget(String path) {
        String normalized = path == null ? "" : path;
        return normalized.contains("${")
                || normalized.contains("#{")
                || normalized.contains("<%")
                || normalized.contains("%>");
    }

    private static String extractFormsAndInputs(
            String line,
            SourceLocation location,
            List<JspFormInfo> forms,
            List<JspInputInfo> inputs,
            String currentFormKey) {
        List<JspLineEvent> events = new ArrayList<>();
        Matcher matcher = FORM.matcher(line);
        while (matcher.find()) {
            String sourceTag = matcher.group(1).toLowerCase(Locale.ROOT);
            Map<String, String> attributes = attributes(matcher.group(2));
            SourceLocation formLocation = new SourceLocation(
                    location.relativePath(),
                    location.line(),
                    matcher.start());
            JspFormInfo form = new JspFormInfo(
                    attributes.getOrDefault("action", ""),
                    formMethod(attributes, sourceTag),
                    sourceTag,
                    formLocation);
            events.add(new JspLineEvent(matcher.start(), 0, form, null, false));
        }
        addNativeInputEvents(line, location, events, HTML_INPUT, "input", "type", "text");
        addNativeInputEvents(line, location, events, HTML_SELECT, "select", "", "select");
        addNativeInputEvents(line, location, events, HTML_TEXTAREA, "textarea", "", "textarea");
        addTagInputEvents(line, location, events, STRUTS_INPUT, "property", "html:");
        addTagInputEvents(line, location, events, SPRING_INPUT, "path", "form:");
        Matcher closeMatcher = FORM_CLOSE.matcher(line);
        while (closeMatcher.find()) {
            events.add(new JspLineEvent(closeMatcher.start(), 2, null, null, true));
        }
        events.sort((left, right) -> left.start == right.start
                ? Integer.compare(left.order, right.order)
                : Integer.compare(left.start, right.start));
        String formKey = currentFormKey;
        for (JspLineEvent event : events) {
            if (event.form != null) {
                forms.add(event.form);
                formKey = formKey(event.form);
            } else if (event.input != null) {
                inputs.add(new JspInputInfo(
                        event.input.logicalName(),
                        event.input.inputType(),
                        event.input.sourceTag(),
                        formKey,
                        event.input.location()));
            } else if (event.closesForm) {
                formKey = "";
            }
        }
        return formKey;
    }

    private static void addNativeInputEvents(
            String line,
            SourceLocation location,
            List<JspLineEvent> events,
            Pattern pattern,
            String sourceTag,
            String typeAttribute,
            String defaultType) {
        Matcher matcher = pattern.matcher(line);
        while (matcher.find()) {
            Map<String, String> attributes = attributes(matcher.group(1));
            String name = attributes.getOrDefault("name", "");
            if (!name.isBlank()) {
                String inputType = typeAttribute.isBlank()
                        ? defaultType
                        : attributes.getOrDefault(typeAttribute, defaultType);
                JspInputInfo input = new JspInputInfo(name, inputType, sourceTag, location);
                events.add(new JspLineEvent(matcher.start(), 1, null, input, false));
            }
        }
    }

    private static String formMethod(Map<String, String> attributes, String sourceTag) {
        String method = attributes.get("method");
        return method == null || method.isBlank() ? defaultFormMethod(sourceTag) : method;
    }

    private static String defaultFormMethod(String sourceTag) {
        String normalized = sourceTag == null ? "" : sourceTag.toLowerCase(Locale.ROOT);
        return normalized.equals("html:form") || normalized.equals("form:form") ? "post" : "get";
    }

    private static void addTagInputEvents(
            String line,
            SourceLocation location,
            List<JspLineEvent> events,
            Pattern pattern,
            String nameAttribute,
            String sourcePrefix) {
        Matcher matcher = pattern.matcher(line);
        while (matcher.find()) {
            Map<String, String> attributes = attributes(matcher.group(2));
            String name = attributes.getOrDefault(nameAttribute, "");
            if (!name.isBlank()) {
                JspInputInfo input = new JspInputInfo(name, matcher.group(1), sourcePrefix + matcher.group(1), location);
                events.add(new JspLineEvent(matcher.start(), 1, null, input, false));
            }
        }
    }

    private static void extractRequestAccesses(
            String line,
            String jspCode,
            SourceLocation location,
            List<JspRequestParameterAccessInfo> requestParameters) {
        addRequestAccesses(line, location, requestParameters, EL_PARAM, JspRequestParameterAccessKind.READ);
        addBracketRequestAccesses(line, location, requestParameters);
        addRequestAccesses(jspCode, location, requestParameters, GET_PARAMETER, JspRequestParameterAccessKind.READ);
    }

    private static void addBracketRequestAccesses(
            String line,
            SourceLocation location,
            List<JspRequestParameterAccessInfo> requestParameters) {
        Matcher matcher = EL_PARAM_BRACKET.matcher(line);
        while (matcher.find()) {
            String name = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            requestParameters.add(new JspRequestParameterAccessInfo(name, JspRequestParameterAccessKind.READ, location));
        }
    }

    private static void addRequestAccesses(
            String line,
            SourceLocation location,
            List<JspRequestParameterAccessInfo> requestParameters,
            Pattern pattern,
            JspRequestParameterAccessKind accessKind) {
        Matcher matcher = pattern.matcher(line);
        while (matcher.find()) {
            requestParameters.add(new JspRequestParameterAccessInfo(matcher.group(1), accessKind, location));
        }
    }

    private static Map<String, String> attributes(String text) {
        Map<String, String> result = new LinkedHashMap<>();
        Matcher matcher = ATTRIBUTE.matcher(text == null ? "" : text);
        while (matcher.find()) {
            String value = matcher.group(2) != null
                    ? matcher.group(2)
                    : matcher.group(3) != null ? matcher.group(3) : matcher.group(4);
            String name = matcher.group(1);
            result.put(name, value);
            result.putIfAbsent(name.toLowerCase(Locale.ROOT), value);
        }
        return result;
    }

    private static JspAnalysisResult emptyResult(JspParserMode parserMode) {
        return new JspAnalysisResult(
                parserMode,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private static SourceLocation location(Path webRoot, Path jspFile, int lineNumber) {
        String relativePath = webRoot.toAbsolutePath().normalize()
                .relativize(jspFile.toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');
        return new SourceLocation(relativePath, lineNumber, 1);
    }

    private static String formKey(JspFormInfo form) {
        return form.action() + ":" + form.method() + ":" + form.location().line()
                + ":" + form.location().column();
    }

    private record JspLineEvent(
            int start,
            int order,
            JspFormInfo form,
            JspInputInfo input,
            boolean closesForm) {
    }

    private record JspMaskScan(String text, boolean open) {
    }

    private record JspMarkupBuffer(String text, SourceLocation location) {
    }

    private record JspMarkupLine(String text, SourceLocation location, JspMarkupBuffer buffer) {
    }
}
