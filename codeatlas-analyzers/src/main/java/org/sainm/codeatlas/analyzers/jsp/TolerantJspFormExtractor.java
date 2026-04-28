package org.sainm.codeatlas.analyzers.jsp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TolerantJspFormExtractor {
    private static final Pattern FORM_PATTERN = Pattern.compile(
        "(?is)<(?:html:form|form)\\b([^>]*)>(.*?)</(?:html:form|form)>"
    );
    private static final Pattern INPUT_PATTERN = Pattern.compile(
        "(?is)<(?:html:text|html:password|html:hidden|html:checkbox|html:select|html:textarea|html:radio|html:multibox|input|select|textarea)\\b([^>]*)>"
    );
    private static final Pattern ATTR_PATTERN = Pattern.compile("(?is)([a-zA-Z_:.-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");

    List<JspForm> extract(String jspText) {
        List<JspForm> forms = new ArrayList<>();
        Matcher formMatcher = FORM_PATTERN.matcher(jspText);
        while (formMatcher.find()) {
            String attributes = formMatcher.group(1);
            String body = formMatcher.group(2);
            String action = firstAttr(attributes, "action", "path");
            if (action == null) {
                continue;
            }
            String method = attr(attributes, "method");
            List<JspInput> inputs = inputs(body, lineOf(jspText, formMatcher.start(2)));
            forms.add(new JspForm(action, method, lineOf(jspText, formMatcher.start()), inputs));
        }
        return forms;
    }

    private List<JspInput> inputs(String body, int bodyStartLine) {
        List<JspInput> inputs = new ArrayList<>();
        Matcher matcher = INPUT_PATTERN.matcher(body);
        while (matcher.find()) {
            String attrs = matcher.group(1);
            String name = firstAttr(attrs, "property", "name");
            if (name == null) {
                continue;
            }
            String type = attr(attrs, "type");
            if (type == null) {
                type = tagType(matcher.group());
            }
            inputs.add(new JspInput(name, type, bodyStartLine + lineOf(body, matcher.start()) - 1));
        }
        return inputs;
    }

    private String tagType(String tag) {
        String lower = tag.toLowerCase(Locale.ROOT);
        if (lower.startsWith("<html:")) {
            int end = lower.indexOf(' ');
            String local = end < 0 ? lower.substring(6, lower.length() - 1) : lower.substring(6, end);
            return local;
        }
        if (lower.startsWith("<select")) {
            return "select";
        }
        if (lower.startsWith("<textarea")) {
            return "textarea";
        }
        return "text";
    }

    private String firstAttr(String attributes, String first, String second) {
        String value = attr(attributes, first);
        return value == null ? attr(attributes, second) : value;
    }

    private String attr(String attributes, String name) {
        Matcher matcher = ATTR_PATTERN.matcher(attributes);
        while (matcher.find()) {
            if (matcher.group(1).equalsIgnoreCase(name)) {
                if (matcher.group(3) != null) {
                    return matcher.group(3).trim();
                }
                if (matcher.group(4) != null) {
                    return matcher.group(4).trim();
                }
                return matcher.group(5).trim();
            }
        }
        return null;
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
}
