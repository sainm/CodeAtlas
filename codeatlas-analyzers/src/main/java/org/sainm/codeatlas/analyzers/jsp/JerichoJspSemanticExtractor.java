package org.sainm.codeatlas.analyzers.jsp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.htmlparser.jericho.Element;
import net.htmlparser.jericho.Source;
import net.htmlparser.jericho.StartTag;

public final class JerichoJspSemanticExtractor {
    private final StrutsJspTagAdapter strutsTagAdapter = new StrutsJspTagAdapter();
    private final SpringJspTagAdapter springTagAdapter = new SpringJspTagAdapter();

    public JspSemanticAnalysis extract(String jspText, WebAppContext context) {
        JspSemanticAnalysis scannerAnalysis = scannerAnalysis(jspText, context);
        JspSemanticAnalysis jerichoAnalysis = jerichoAnalysis(jspText, context, scannerAnalysis);
        return merge(jerichoAnalysis, scannerAnalysis);
    }

    private JspSemanticAnalysis scannerAnalysis(String jspText, WebAppContext context) {
        return new TolerantJspSemanticExtractor(false).scannerOnly(jspText, context);
    }

    private JspSemanticAnalysis jerichoAnalysis(String jspText, WebAppContext context, JspSemanticAnalysis scannerAnalysis) {
        Source source = new Source(jspText);
        source.fullSequentialParse();
        Set<String> tagdirPrefixes = tagdirPrefixes(scannerAnalysis);
        List<Range> ignoredRanges = ignoredScriptStyleRanges(jspText);
        List<JspAction> actions = new ArrayList<>();
        for (Element element : source.getAllElements()) {
            StartTag startTag = element.getStartTag();
            if (startTag == null) {
                continue;
            }
            if (insideAny(startTag.getBegin(), ignoredRanges)) {
                continue;
            }
            String name = startTag.getName();
            if (!isKnownAction(name, tagdirPrefixes)) {
                continue;
            }
            actions.add(new JspAction(name, attributes(startTag), JspTextScanner.lineOf(jspText, startTag.getBegin())));
        }
        return new JspSemanticAnalysis(
            List.of(),
            actions,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            context.defaultEncoding(),
            JspSemanticParserSource.JERICHO_WITH_TOKENIZER_MERGE,
            "jericho-html",
            null,
            List.of()
        );
    }

    private List<Range> ignoredScriptStyleRanges(String text) {
        List<Range> ranges = new ArrayList<>();
        int offset = 0;
        while (offset < text.length()) {
            int script = indexOfIgnoreCase(text, "<script", offset);
            int style = indexOfIgnoreCase(text, "<style", offset);
            int start;
            String name;
            if (script < 0 && style < 0) {
                break;
            } else if (script >= 0 && (style < 0 || script < style)) {
                start = script;
                name = "script";
            } else {
                start = style;
                name = "style";
            }
            int openEnd = tagEnd(text, start + 1);
            if (openEnd < 0) {
                ranges.add(new Range(start, text.length()));
                break;
            }
            int closeStart = indexOfIgnoreCase(text, "</" + name, openEnd + 1);
            if (closeStart < 0) {
                ranges.add(new Range(start, openEnd + 1));
                offset = openEnd + 1;
                continue;
            }
            int closeEnd = tagEnd(text, closeStart + 2);
            int end = closeEnd < 0 ? text.length() : closeEnd + 1;
            ranges.add(new Range(start, end));
            offset = end;
        }
        return List.copyOf(ranges);
    }

    private boolean insideAny(int offset, List<Range> ranges) {
        for (Range range : ranges) {
            if (offset >= range.start() && offset < range.end()) {
                return true;
            }
        }
        return false;
    }

    private int tagEnd(String text, int offset) {
        char quote = 0;
        for (int i = offset; i < text.length(); i++) {
            char current = text.charAt(i);
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
                return i;
            }
        }
        return -1;
    }

    private int indexOfIgnoreCase(String text, String needle, int offset) {
        return text.toLowerCase(java.util.Locale.ROOT).indexOf(needle.toLowerCase(java.util.Locale.ROOT), offset);
    }

    private Set<String> tagdirPrefixes(JspSemanticAnalysis scannerAnalysis) {
        Set<String> prefixes = new LinkedHashSet<>();
        for (JspTaglibReference taglib : scannerAnalysis.taglibs()) {
            if (taglib.tagdir() != null) {
                prefixes.add(taglib.prefix());
            }
        }
        return prefixes;
    }

    private Map<String, String> attributes(StartTag startTag) {
        Map<String, String> result = new LinkedHashMap<>();
        startTag.getAttributes().forEach(attribute -> result.put(attribute.getName(), attribute.getValue() == null ? "" : attribute.getValue()));
        return result;
    }

    private boolean isKnownAction(String name, Set<String> tagdirPrefixes) {
        if (name == null || name.isBlank()) {
            return false;
        }
        if (name.startsWith("jsp:")
            || name.startsWith("c:")
            || strutsTagAdapter.isActionTag(name)
            || springTagAdapter.isActionTag(name)) {
            return true;
        }
        int separator = name.indexOf(':');
        return separator > 0 && tagdirPrefixes.contains(name.substring(0, separator));
    }

    private JspSemanticAnalysis merge(JspSemanticAnalysis first, JspSemanticAnalysis second) {
        return new JspSemanticAnalysis(
            second.directives(),
            mergeActions(first.actions(), second.actions()),
            second.expressions(),
            second.taglibs(),
            second.clientNavigations(),
            second.includes(),
            second.encoding(),
            JspSemanticParserSource.JERICHO_WITH_TOKENIZER_MERGE,
            "jericho-html+tolerant-jsp-tokenizer",
            null,
            List.of()
        );
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

    private record Range(int start, int end) {
    }
}
