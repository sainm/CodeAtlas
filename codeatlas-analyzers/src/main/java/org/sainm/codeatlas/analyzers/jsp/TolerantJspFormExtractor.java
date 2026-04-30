package org.sainm.codeatlas.analyzers.jsp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class TolerantJspFormExtractor {
    private final StrutsJspTagAdapter strutsTagAdapter = new StrutsJspTagAdapter();
    private final SpringJspTagAdapter springTagAdapter = new SpringJspTagAdapter();

    List<JspForm> extract(String jspText) {
        List<JspTextScanner.TagToken> tags = JspTextScanner.tags(jspText);
        List<JspForm> forms = new ArrayList<>();
        for (int i = 0; i < tags.size(); i++) {
            JspTextScanner.TagToken tag = tags.get(i);
            if (tag.closing() || !isFormTag(tag.name())) {
                continue;
            }
            Map<String, String> attributes = tag.attributes();
            String action = firstAttr(attributes, "action", "path");
            if (action == null) {
                continue;
            }
            String method = attr(attributes, "method");
            List<JspInput> inputs = inputs(tags, i + 1, tag.name());
            forms.add(new JspForm(action, method, tag.line(), inputs));
        }
        return forms;
    }

    private List<JspInput> inputs(List<JspTextScanner.TagToken> tags, int startIndex, String formTagName) {
        List<JspInput> inputs = new ArrayList<>();
        int depth = 1;
        for (int i = startIndex; i < tags.size() && depth > 0; i++) {
            JspTextScanner.TagToken tag = tags.get(i);
            if (tag.name().equals(formTagName)) {
                if (tag.closing()) {
                    depth--;
                    continue;
                }
                if (!tag.selfClosing()) {
                    depth++;
                }
            }
            if (tag.closing() || !isInputTag(tag.name())) {
                continue;
            }
            Map<String, String> attributes = tag.attributes();
            String name = firstAttr(attributes, "property", "name", "path");
            if (name == null) {
                continue;
            }
            String type = attr(attributes, "type");
            if (type == null) {
                type = tagType(tag.name());
            }
            inputs.add(new JspInput(name, type, tag.line()));
        }
        return inputs;
    }

    private String tagType(String tagName) {
        String lower = tagName.toLowerCase(Locale.ROOT);
        if (lower.startsWith("html:")) {
            return lower.substring("html:".length());
        }
        if (lower.startsWith("form:")) {
            return lower.substring("form:".length());
        }
        if (lower.equals("select")) {
            return "select";
        }
        if (lower.equals("textarea")) {
            return "textarea";
        }
        return "text";
    }

    private String firstAttr(Map<String, String> attributes, String first, String second) {
        return firstAttr(attributes, first, second, null);
    }

    private String firstAttr(Map<String, String> attributes, String first, String second, String third) {
        String value = attr(attributes, first);
        if (value != null) {
            return value;
        }
        value = attr(attributes, second);
        return value == null && third != null ? attr(attributes, third) : value;
    }

    private String attr(Map<String, String> attributes, String name) {
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue().trim();
            }
        }
        return null;
    }

    private boolean isFormTag(String tagName) {
        return strutsTagAdapter.isFormTag(tagName) || springTagAdapter.isFormTag(tagName);
    }

    private boolean isInputTag(String tagName) {
        return strutsTagAdapter.isInputTag(tagName) || springTagAdapter.isInputTag(tagName);
    }
}
