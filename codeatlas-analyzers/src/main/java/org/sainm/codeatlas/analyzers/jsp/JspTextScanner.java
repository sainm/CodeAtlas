package org.sainm.codeatlas.analyzers.jsp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class JspTextScanner {
    private JspTextScanner() {
    }

    static List<TagToken> tags(String text) {
        List<TagToken> tags = new ArrayList<>();
        int offset = 0;
        while (offset < text.length()) {
            int start = text.indexOf('<', offset);
            if (start < 0) {
                break;
            }
            if (startsWithIgnoreCase(text, start, "<script")) {
                offset = afterClosingTag(text, start, "script");
                continue;
            }
            if (startsWithIgnoreCase(text, start, "<style")) {
                offset = afterClosingTag(text, start, "style");
                continue;
            }
            if (startsWith(text, start, "<%") || startsWith(text, start, "<!") || startsWith(text, start, "<?")) {
                offset = start + 2;
                continue;
            }
            int end = tagEnd(text, start + 1);
            if (end < 0) {
                break;
            }
            TagToken token = tag(text, start, end);
            if (token != null) {
                tags.add(token);
            }
            offset = end + 1;
        }
        return tags;
    }

    static List<DirectiveToken> directives(String text) {
        List<DirectiveToken> directives = new ArrayList<>();
        int offset = 0;
        while (offset < text.length()) {
            int start = text.indexOf("<%@", offset);
            if (start < 0) {
                break;
            }
            int contentStart = start + 3;
            int end = text.indexOf("%>", contentStart);
            if (end < 0) {
                break;
            }
            String content = text.substring(contentStart, end).trim();
            int nameEnd = readNameEnd(content, 0);
            if (nameEnd > 0) {
                String name = content.substring(0, nameEnd);
                String attrs = content.substring(nameEnd);
                directives.add(new DirectiveToken(name, attributes(attrs), lineOf(text, start)));
            }
            offset = end + 2;
        }
        return directives;
    }

    static List<JspExpressionFragment> expressions(String text) {
        List<JspExpressionFragment> expressions = new ArrayList<>();
        collectEl(text, expressions);
        collectJspCode(text, expressions);
        return expressions;
    }

    static Map<String, String> attributes(String text) {
        Map<String, String> attributes = new LinkedHashMap<>();
        int offset = 0;
        while (offset < text.length()) {
            offset = skipWhitespace(text, offset);
            int nameStart = offset;
            int nameEnd = readAttributeNameEnd(text, offset);
            if (nameEnd <= nameStart) {
                offset++;
                continue;
            }
            String name = text.substring(nameStart, nameEnd);
            offset = skipWhitespace(text, nameEnd);
            if (offset >= text.length() || text.charAt(offset) != '=') {
                attributes.put(name, "");
                continue;
            }
            offset = skipWhitespace(text, offset + 1);
            ValueRead value = readAttributeValue(text, offset);
            attributes.put(name, value.value().trim());
            offset = value.nextOffset();
        }
        return attributes;
    }

    static int lineOf(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    static int skipWhitespace(String text, int offset) {
        int i = offset;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    static int readIdentifierEnd(String text, int offset) {
        int i = offset;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '$') {
                break;
            }
            i++;
        }
        return i;
    }

    static QuotedValue readQuotedValue(String text, int offset) {
        if (offset >= text.length()) {
            return null;
        }
        char quote = text.charAt(offset);
        if (quote != '"' && quote != '\'') {
            return null;
        }
        StringBuilder value = new StringBuilder();
        int i = offset + 1;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                value.append(text.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == quote) {
                return new QuotedValue(value.toString(), i + 1);
            }
            value.append(c);
            i++;
        }
        return null;
    }

    private static void collectEl(String text, List<JspExpressionFragment> expressions) {
        int offset = 0;
        while (offset < text.length()) {
            int start = text.indexOf("${", offset);
            if (start < 0) {
                break;
            }
            int end = text.indexOf('}', start + 2);
            if (end < 0) {
                break;
            }
            expressions.add(new JspExpressionFragment("EL", text.substring(start + 2, end), lineOf(text, start)));
            offset = end + 1;
        }
    }

    private static void collectJspCode(String text, List<JspExpressionFragment> expressions) {
        int offset = 0;
        while (offset < text.length()) {
            int start = text.indexOf("<%", offset);
            if (start < 0) {
                break;
            }
            int end = text.indexOf("%>", start + 2);
            if (end < 0) {
                break;
            }
            if (startsWith(text, start, "<%@")) {
                offset = end + 2;
                continue;
            }
            if (startsWith(text, start, "<%=")) {
                expressions.add(new JspExpressionFragment("EXPRESSION", text.substring(start + 3, end), lineOf(text, start)));
            } else {
                expressions.add(new JspExpressionFragment("SCRIPTLET", text.substring(start + 2, end), lineOf(text, start)));
            }
            offset = end + 2;
        }
    }

    private static TagToken tag(String text, int start, int end) {
        int offset = start + 1;
        boolean closing = false;
        if (offset < text.length() && text.charAt(offset) == '/') {
            closing = true;
            offset++;
        }
        offset = skipWhitespace(text, offset);
        int nameStart = offset;
        int nameEnd = readTagNameEnd(text, offset);
        if (nameEnd <= nameStart) {
            return null;
        }
        String name = text.substring(nameStart, nameEnd);
        String attributes = closing ? "" : text.substring(nameEnd, endBeforeSelfClosingSlash(text, nameEnd, end));
        boolean selfClosing = !closing && isSelfClosing(text, start, end);
        return new TagToken(name, attributes, start, end + 1, closing, selfClosing, lineOf(text, start));
    }

    private static int tagEnd(String text, int offset) {
        char quote = 0;
        for (int i = offset; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                continue;
            }
            if (c == '>') {
                return i;
            }
        }
        return -1;
    }

    private static int afterClosingTag(String text, int start, String name) {
        int openEnd = tagEnd(text, start + 1);
        if (openEnd < 0) {
            return text.length();
        }
        String close = "</" + name;
        int closeStart = indexOfIgnoreCase(text, close, openEnd + 1);
        if (closeStart < 0) {
            return openEnd + 1;
        }
        int closeEnd = tagEnd(text, closeStart + 2);
        return closeEnd < 0 ? text.length() : closeEnd + 1;
    }

    private static int indexOfIgnoreCase(String text, String needle, int offset) {
        String lowerText = text.toLowerCase(Locale.ROOT);
        return lowerText.indexOf(needle.toLowerCase(Locale.ROOT), offset);
    }

    private static int readNameEnd(String text, int offset) {
        int i = offset;
        while (i < text.length() && Character.isLetterOrDigit(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private static int readTagNameEnd(String text, int offset) {
        int i = offset;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != ':' && c != '_' && c != '-' && c != '.') {
                break;
            }
            i++;
        }
        return i;
    }

    private static int readAttributeNameEnd(String text, int offset) {
        int i = offset;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != ':' && c != '_' && c != '-' && c != '.') {
                break;
            }
            i++;
        }
        return i;
    }

    private static ValueRead readAttributeValue(String text, int offset) {
        QuotedValue quoted = readQuotedValue(text, offset);
        if (quoted != null) {
            return new ValueRead(quoted.value(), quoted.nextOffset());
        }
        int i = offset;
        while (i < text.length() && !Character.isWhitespace(text.charAt(i)) && text.charAt(i) != '>') {
            i++;
        }
        return new ValueRead(text.substring(offset, i), i);
    }

    private static int endBeforeSelfClosingSlash(String text, int nameEnd, int tagEnd) {
        int end = tagEnd;
        while (end > nameEnd && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        if (end > nameEnd && text.charAt(end - 1) == '/') {
            end--;
        }
        return end;
    }

    private static boolean isSelfClosing(String text, int start, int tagEnd) {
        int i = tagEnd - 1;
        while (i > start && Character.isWhitespace(text.charAt(i))) {
            i--;
        }
        return i > start && text.charAt(i) == '/';
    }

    private static boolean startsWith(String text, int offset, String prefix) {
        return text.regionMatches(offset, prefix, 0, prefix.length());
    }

    private static boolean startsWithIgnoreCase(String text, int offset, String prefix) {
        return text.regionMatches(true, offset, prefix, 0, prefix.length());
    }

    record TagToken(String name, String attributesText, int startOffset, int endOffset, boolean closing, boolean selfClosing, int line) {
        Map<String, String> attributes() {
            return JspTextScanner.attributes(attributesText);
        }
    }

    record DirectiveToken(String name, Map<String, String> attributes, int line) {
    }

    record QuotedValue(String value, int nextOffset) {
    }

    private record ValueRead(String value, int nextOffset) {
    }
}
