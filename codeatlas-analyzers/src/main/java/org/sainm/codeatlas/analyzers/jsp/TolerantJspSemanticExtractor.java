package org.sainm.codeatlas.analyzers.jsp;

import org.sainm.codeatlas.graph.model.Confidence;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TolerantJspSemanticExtractor {
    private static final Set<String> STANDARD_ACTIONS = Set.of("jsp:include", "jsp:forward", "jsp:param", "jsp:useBean");
    private static final Set<String> JSTL_ACTIONS = Set.of("c:if", "c:forEach", "c:choose", "c:when", "c:otherwise", "c:out", "c:set");
    private final ApacheJasperJspSemanticExtractor jasperExtractor = new ApacheJasperJspSemanticExtractor();
    private final StrutsJspTagAdapter strutsTagAdapter = new StrutsJspTagAdapter();
    private final SpringJspTagAdapter springTagAdapter = new SpringJspTagAdapter();

    public JspSemanticAnalysis extract(String jspText, WebAppContext context) {
        return extract(jspText, context, null);
    }

    public JspSemanticAnalysis extract(String jspText, WebAppContext context, Path jspFile) {
        JspSemanticAnalysis scannerAnalysis = scannerAnalysis(jspText, context);
        JspSemanticAnalysis jasperAnalysis = jasperExtractor.extract(jspFile, context);
        if (jasperAnalysis == null) {
            return withFallbackReason(scannerAnalysis, fallbackReason(jspFile, context));
        }
        return merge(jasperAnalysis, scannerAnalysis);
    }

    static JspTaglibReference taglib(JspDirective directive, WebAppContext context) {
        String prefix = directive.attributes().get("prefix");
        String uri = directive.attributes().get("uri");
        String tagdir = directive.attributes().get("tagdir");
        String resolved = null;
        Confidence confidence = Confidence.POSSIBLE;
        if (uri != null && !uri.isBlank()) {
            resolved = context.taglibs().get(uri);
            confidence = resolved == null ? Confidence.LIKELY : Confidence.CERTAIN;
        } else if (tagdir != null && !tagdir.isBlank()) {
            resolved = tagdir;
            confidence = Confidence.CERTAIN;
        }
        return new JspTaglibReference(prefix, uri, tagdir, resolved, confidence, directive.line());
    }

    private JspSemanticAnalysis scannerAnalysis(String jspText, WebAppContext context) {
        List<JspDirective> directives = directives(jspText);
        List<JspTaglibReference> taglibs = taglibs(directives, context);
        List<JspAction> actions = actions(jspText, taglibs);
        List<JspExpressionFragment> expressions = JspTextScanner.expressions(jspText);
        List<String> includes = directives.stream()
            .filter(directive -> directive.name().equals("include"))
            .map(directive -> directive.attributes().get("file"))
            .filter(value -> value != null && !value.isBlank())
            .toList();
        String encoding = directives.stream()
            .filter(directive -> directive.name().equals("page"))
            .map(directive -> firstNonBlank(directive.attributes().get("pageEncoding"), directive.attributes().get("contentType")))
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElse(context.defaultEncoding());
        return new JspSemanticAnalysis(
            directives,
            actions,
            expressions,
            taglibs,
            List.of(),
            includes,
            encoding,
            JspSemanticParserSource.TOKENIZER_FALLBACK,
            "tolerant-jsp-tokenizer",
            null
        );
    }

    private List<JspDirective> directives(String text) {
        return JspTextScanner.directives(text).stream()
            .map(token -> new JspDirective(token.name(), token.attributes(), token.line()))
            .toList();
    }

    private List<JspTaglibReference> taglibs(List<JspDirective> directives, WebAppContext context) {
        return directives.stream()
            .filter(directive -> directive.name().equals("taglib"))
            .map(directive -> taglib(directive, context))
            .toList();
    }

    private List<JspAction> actions(String text, List<JspTaglibReference> taglibs) {
        List<JspAction> result = new ArrayList<>();
        Set<String> tagdirPrefixes = new LinkedHashSet<>();
        for (JspTaglibReference taglib : taglibs) {
            if (taglib.tagdir() != null && taglib.prefix() != null && !taglib.prefix().isBlank()) {
                tagdirPrefixes.add(taglib.prefix());
            }
        }
        for (JspTextScanner.TagToken tag : JspTextScanner.tags(text)) {
            if (tag.closing()) {
                continue;
            }
            if (isKnownAction(tag.name(), tagdirPrefixes)) {
                result.add(new JspAction(tag.name(), tag.attributes(), tag.line()));
            }
        }
        return result;
    }

    private boolean isKnownAction(String name, Set<String> tagdirPrefixes) {
        if (STANDARD_ACTIONS.contains(name)
            || JSTL_ACTIONS.contains(name)
            || strutsTagAdapter.isActionTag(name)
            || springTagAdapter.isActionTag(name)) {
            return true;
        }
        int separator = name.indexOf(':');
        return separator > 0 && tagdirPrefixes.contains(name.substring(0, separator));
    }

    private JspSemanticAnalysis merge(JspSemanticAnalysis first, JspSemanticAnalysis second) {
        List<JspDirective> directives = mergeDirectives(first.directives(), second.directives());
        List<JspAction> actions = mergeActions(first.actions(), second.actions());
        List<JspExpressionFragment> expressions = mergeExpressions(first.expressions(), second.expressions());
        List<JspTaglibReference> taglibs = mergeTaglibs(first.taglibs(), second.taglibs());
        List<String> includes = mergeStrings(first.includes(), second.includes());
        String encoding = first.encoding() == null || first.encoding().isBlank() ? second.encoding() : first.encoding();
        return new JspSemanticAnalysis(
            directives,
            actions,
            expressions,
            taglibs,
            List.of(),
            includes,
            encoding,
            JspSemanticParserSource.JASPER_WITH_TOKENIZER_MERGE,
            "apache-jasper+tolerant-jsp-tokenizer",
            null
        );
    }

    private JspSemanticAnalysis withFallbackReason(JspSemanticAnalysis analysis, String fallbackReason) {
        return new JspSemanticAnalysis(
            analysis.directives(),
            analysis.actions(),
            analysis.expressions(),
            analysis.taglibs(),
            analysis.clientNavigations(),
            analysis.includes(),
            analysis.encoding(),
            JspSemanticParserSource.TOKENIZER_FALLBACK,
            "tolerant-jsp-tokenizer",
            fallbackReason
        );
    }

    private String fallbackReason(Path jspFile, WebAppContext context) {
        if (jspFile == null) {
            return "jsp file path unavailable for Apache Jasper";
        }
        if (context == null || context.webRoot() == null) {
            return "web application context unavailable for Apache Jasper";
        }
        Path webRoot = context.webRoot().toAbsolutePath().normalize();
        Path jspPath = jspFile.toAbsolutePath().normalize();
        if (!jspPath.startsWith(webRoot)) {
            return "jsp file is outside web root: " + webRoot;
        }
        return "Apache Jasper could not parse this JSP with the available webapp context";
    }

    private List<JspDirective> mergeDirectives(List<JspDirective> first, List<JspDirective> second) {
        Map<String, JspDirective> merged = new LinkedHashMap<>();
        for (JspDirective directive : first) {
            merged.put(directive.name() + directive.line() + directive.attributes(), directive);
        }
        for (JspDirective directive : second) {
            merged.putIfAbsent(directive.name() + directive.line() + directive.attributes(), directive);
        }
        return List.copyOf(merged.values());
    }

    private List<JspAction> mergeActions(List<JspAction> first, List<JspAction> second) {
        Map<String, JspAction> merged = new LinkedHashMap<>();
        for (JspAction action : first) {
            merged.put(action.name() + action.line() + action.attributes(), action);
        }
        for (JspAction action : second) {
            merged.putIfAbsent(action.name() + action.line() + action.attributes(), action);
        }
        return List.copyOf(merged.values());
    }

    private List<JspExpressionFragment> mergeExpressions(List<JspExpressionFragment> first, List<JspExpressionFragment> second) {
        Map<String, JspExpressionFragment> merged = new LinkedHashMap<>();
        for (JspExpressionFragment expression : first) {
            merged.put(expression.kind() + expression.line() + expression.expression(), expression);
        }
        for (JspExpressionFragment expression : second) {
            merged.putIfAbsent(expression.kind() + expression.line() + expression.expression(), expression);
        }
        return List.copyOf(merged.values());
    }

    private List<JspTaglibReference> mergeTaglibs(List<JspTaglibReference> first, List<JspTaglibReference> second) {
        Map<String, JspTaglibReference> merged = new LinkedHashMap<>();
        for (JspTaglibReference taglib : first) {
            merged.put(taglib.prefix() + taglib.line(), taglib);
        }
        for (JspTaglibReference taglib : second) {
            merged.putIfAbsent(taglib.prefix() + taglib.line(), taglib);
        }
        return List.copyOf(merged.values());
    }

    private List<String> mergeStrings(List<String> first, List<String> second) {
        Set<String> merged = new LinkedHashSet<>(first);
        merged.addAll(second);
        return List.copyOf(merged);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
