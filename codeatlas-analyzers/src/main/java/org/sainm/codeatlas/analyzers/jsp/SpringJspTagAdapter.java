package org.sainm.codeatlas.analyzers.jsp;

import org.sainm.codeatlas.graph.model.Confidence;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class SpringJspTagAdapter {
    private static final Set<String> SPRING_ACTION_TAGS = Set.of(
        "form:form",
        "form:input",
        "form:hidden",
        "form:password",
        "form:checkbox",
        "form:checkboxes",
        "form:select",
        "form:option",
        "form:options",
        "form:textarea",
        "form:radiobutton",
        "form:radiobuttons",
        "form:button",
        "form:errors"
    );
    private static final Set<String> SPRING_FORM_TAGS = Set.of("form:form");
    private static final Set<String> SPRING_INPUT_TAGS = Set.of(
        "form:input",
        "form:hidden",
        "form:password",
        "form:checkbox",
        "form:checkboxes",
        "form:select",
        "form:textarea",
        "form:radiobutton",
        "form:radiobuttons",
        "form:button"
    );

    boolean isActionTag(String tagName) {
        return SPRING_ACTION_TAGS.contains(tagName);
    }

    boolean isFormTag(String tagName) {
        return SPRING_FORM_TAGS.contains(tagName);
    }

    boolean isInputTag(String tagName) {
        return SPRING_INPUT_TAGS.contains(tagName);
    }

    List<OptionSourceUse> optionSourceUses(JspSemanticAnalysis analysis) {
        return analysis.actions().stream()
            .filter(action -> action.name().equals("form:options"))
            .map(this::optionSourceUse)
            .flatMap(Optional::stream)
            .toList();
    }

    private Optional<OptionSourceUse> optionSourceUse(JspAction action) {
        String items = action.attributes().get("items");
        if (items == null || items.isBlank()) {
            return Optional.empty();
        }
        String source = normalizeItems(items);
        if (source == null || source.isBlank()) {
            return Optional.empty();
        }
        String qualifier = "form:options:source=" + source;
        return Optional.of(new OptionSourceUse(source, qualifier, action.line(), Confidence.LIKELY));
    }

    private String normalizeItems(String items) {
        String trimmed = items.trim();
        if (trimmed.startsWith("${") && trimmed.endsWith("}") && trimmed.length() > 3) {
            return trimmed.substring(2, trimmed.length() - 1).trim();
        }
        if (trimmed.contains("<%")) {
            return null;
        }
        return trimmed;
    }

    record OptionSourceUse(String sourceName, String qualifier, int line, Confidence confidence) {
    }
}
