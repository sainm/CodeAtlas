package org.sainm.codeatlas.analyzers.jsp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TolerantJspSemanticExtractor {
    private static final Pattern DIRECTIVE = Pattern.compile("(?is)<%@\\s*(\\w+)\\s+([^%]*)%>");
    private static final Pattern JSP_ACTION = Pattern.compile("(?is)<(jsp:(?:include|forward|param|useBean))\\b([^>]*)>");
    private static final Pattern JSTL_ACTION = Pattern.compile("(?is)<(c:(?:if|forEach|choose|when|otherwise|out|set))\\b([^>]*)>");
    private static final Pattern STRUTS_ACTION = Pattern.compile(
        "(?is)<((?:html|bean|logic):(?:form|text|hidden|password|checkbox|select|textarea|radio|multibox|write|iterate|present|notPresent|empty|notEmpty|equal|notEqual))\\b([^>]*)>"
    );
    private static final Pattern EL = Pattern.compile("\\$\\{([^}]*)}");
    private static final Pattern SCRIPTLET = Pattern.compile("(?is)<%(?!@|=)(.*?)%>");
    private static final Pattern EXPRESSION = Pattern.compile("(?is)<%=(.*?)%>");
    private static final Pattern ATTR = Pattern.compile("(?is)([a-zA-Z_:.-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");

    public JspSemanticAnalysis extract(String jspText, WebAppContext context) {
        List<JspDirective> directives = directives(jspText);
        List<JspAction> actions = actions(jspText);
        List<JspExpressionFragment> expressions = expressions(jspText);
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
        return new JspSemanticAnalysis(directives, actions, expressions, includes, encoding);
    }

    private List<JspDirective> directives(String text) {
        List<JspDirective> result = new ArrayList<>();
        Matcher matcher = DIRECTIVE.matcher(text);
        while (matcher.find()) {
            result.add(new JspDirective(matcher.group(1), attrs(matcher.group(2)), lineOf(text, matcher.start())));
        }
        return result;
    }

    private List<JspAction> actions(String text) {
        List<JspAction> result = new ArrayList<>();
        collectActions(text, JSP_ACTION, result);
        collectActions(text, JSTL_ACTION, result);
        collectActions(text, STRUTS_ACTION, result);
        return result;
    }

    private void collectActions(String text, Pattern pattern, List<JspAction> result) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            result.add(new JspAction(matcher.group(1), attrs(matcher.group(2)), lineOf(text, matcher.start())));
        }
    }

    private List<JspExpressionFragment> expressions(String text) {
        List<JspExpressionFragment> result = new ArrayList<>();
        collectExpressions(text, EL, "EL", result);
        collectExpressions(text, SCRIPTLET, "SCRIPTLET", result);
        collectExpressions(text, EXPRESSION, "EXPRESSION", result);
        return result;
    }

    private void collectExpressions(String text, Pattern pattern, String kind, List<JspExpressionFragment> result) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            result.add(new JspExpressionFragment(kind, matcher.group(1), lineOf(text, matcher.start())));
        }
    }

    private Map<String, String> attrs(String text) {
        Map<String, String> attrs = new LinkedHashMap<>();
        Matcher matcher = ATTR.matcher(text);
        while (matcher.find()) {
            String value = matcher.group(3) != null ? matcher.group(3) : matcher.group(4) != null ? matcher.group(4) : matcher.group(5);
            attrs.put(matcher.group(1), value == null ? "" : value.trim());
        }
        return attrs;
    }

    private int lineOf(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
