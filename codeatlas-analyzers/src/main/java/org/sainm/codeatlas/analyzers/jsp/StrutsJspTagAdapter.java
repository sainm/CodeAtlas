package org.sainm.codeatlas.analyzers.jsp;

import org.sainm.codeatlas.graph.model.Confidence;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class StrutsJspTagAdapter {
    private static final Set<String> STRUTS_ACTION_TAGS = Set.of(
        "html:form",
        "html:text",
        "html:hidden",
        "html:password",
        "html:checkbox",
        "html:select",
        "html:option",
        "html:options",
        "html:optionsCollection",
        "html:textarea",
        "html:radio",
        "html:multibox",
        "html:file",
        "html:image",
        "html:link",
        "html:submit",
        "html:cancel",
        "html:reset",
        "html:errors",
        "html:button",
        "html:rewrite",
        "html:img",
        "bean:write",
        "logic:iterate",
        "logic:present",
        "logic:notPresent",
        "logic:empty",
        "logic:notEmpty",
        "logic:equal",
        "logic:notEqual",
        "logic:forward",
        "logic:redirect",
        "logic:messagesPresent",
        "logic:messagesNotPresent",
        "tiles:insert",
        "tiles:put",
        "tiles:getAsString"
    );
    private static final Set<String> STRUTS_FORM_TAGS = Set.of("html:form", "form");
    private static final Set<String> STRUTS_INPUT_TAGS = Set.of(
        "html:text",
        "html:password",
        "html:hidden",
        "html:checkbox",
        "html:select",
        "html:textarea",
        "html:radio",
        "html:multibox",
        "html:file",
        "html:image",
        "html:submit",
        "html:cancel",
        "html:reset",
        "html:button",
        "input",
        "select",
        "textarea",
        "button"
    );

    boolean isActionTag(String tagName) {
        return STRUTS_ACTION_TAGS.contains(tagName);
    }

    boolean isFormTag(String tagName) {
        return STRUTS_FORM_TAGS.contains(tagName);
    }

    boolean isInputTag(String tagName) {
        return STRUTS_INPUT_TAGS.contains(tagName);
    }

    List<JspNavigation> navigations(JspSemanticAnalysis analysis) {
        return analysis.actions().stream()
            .map(this::navigation)
            .flatMap(Optional::stream)
            .toList();
    }

    List<ForwardConfigUse> forwardConfigUses(JspSemanticAnalysis analysis) {
        return analysis.actions().stream()
            .filter(action -> action.name().equals("logic:forward"))
            .map(this::forwardConfigUse)
            .flatMap(Optional::stream)
            .toList();
    }

    List<OptionSourceUse> optionSourceUses(JspSemanticAnalysis analysis) {
        return analysis.actions().stream()
            .map(this::optionSourceUse)
            .flatMap(Optional::stream)
            .toList();
    }

    List<BeanRead> beanReads(JspSemanticAnalysis analysis) {
        return analysis.actions().stream()
            .map(this::beanRead)
            .flatMap(Optional::stream)
            .toList();
    }

    List<LinkParameter> linkParameters(JspSemanticAnalysis analysis) {
        return analysis.actions().stream()
            .filter(action -> action.name().equals("html:link"))
            .map(this::linkParameter)
            .flatMap(Optional::stream)
            .toList();
    }

    List<TilesDefinitionUse> tilesDefinitionUses(JspSemanticAnalysis analysis) {
        return analysis.actions().stream()
            .filter(action -> action.name().equals("tiles:insert"))
            .map(this::tilesDefinitionUse)
            .flatMap(Optional::stream)
            .toList();
    }

    List<TilesJspInclude> tilesJspIncludes(JspSemanticAnalysis analysis) {
        return analysis.actions().stream()
            .map(this::tilesJspInclude)
            .flatMap(Optional::stream)
            .toList();
    }

    Optional<JspNavigation> navigation(JspAction action) {
        if (action.name().equals("html:link")) {
            String target = firstNonBlank(action.attributes().get("action"), action.attributes().get("page"));
            if (target == null || isDynamic(target)) {
                return Optional.empty();
            }
            return Optional.of(new JspNavigation(target, "html-link:" + target, action.line(), Confidence.LIKELY));
        }
        if (action.name().equals("logic:redirect")) {
            String target = firstNonBlank(firstNonBlank(action.attributes().get("action"), action.attributes().get("page")), action.attributes().get("href"));
            if (target == null || isDynamic(target)) {
                return Optional.empty();
            }
            return Optional.of(new JspNavigation(target, "logic-redirect:" + target, action.line(), Confidence.LIKELY));
        }
        return Optional.empty();
    }

    private Optional<LinkParameter> linkParameter(JspAction action) {
        String paramId = action.attributes().get("paramId");
        if (paramId == null || paramId.isBlank() || isDynamic(paramId)) {
            return Optional.empty();
        }
        String target = firstNonBlank(action.attributes().get("action"), action.attributes().get("page"));
        if (target == null || isDynamic(target)) {
            return Optional.empty();
        }
        String source = firstNonBlank(action.attributes().get("paramName"), action.attributes().get("paramProperty"));
        return Optional.of(new LinkParameter(
            paramId.trim(),
            source,
            target,
            "html-link-param:" + paramId.trim(),
            action.line(),
            Confidence.LIKELY
        ));
    }

    private Optional<TilesDefinitionUse> tilesDefinitionUse(JspAction action) {
        String definition = action.attributes().get("definition");
        if (definition == null || definition.isBlank() || isDynamic(definition)) {
            return Optional.empty();
        }
        return Optional.of(new TilesDefinitionUse(definition.trim(), "tiles-insert:" + definition.trim(), action.line(), Confidence.LIKELY));
    }

    private Optional<ForwardConfigUse> forwardConfigUse(JspAction action) {
        String forwardName = action.attributes().get("name");
        if (forwardName == null || forwardName.isBlank() || isDynamic(forwardName)) {
            return Optional.empty();
        }
        return Optional.of(new ForwardConfigUse(forwardName.trim(), "logic-forward:" + forwardName.trim(), action.line(), Confidence.LIKELY));
    }

    private Optional<OptionSourceUse> optionSourceUse(JspAction action) {
        if (!action.name().equals("html:options") && !action.name().equals("html:optionsCollection")) {
            return Optional.empty();
        }
        String sourceName = firstNonBlank(firstNonBlank(action.attributes().get("collection"), action.attributes().get("name")), action.attributes().get("property"));
        if (sourceName == null || isDynamic(sourceName)) {
            return Optional.empty();
        }
        String valueProperty = firstNonBlank(action.attributes().get("value"), action.attributes().get("property"));
        String labelProperty = firstNonBlank(action.attributes().get("label"), action.attributes().get("labelProperty"));
        String qualifier = action.name() + ":source=" + sourceName.trim();
        return Optional.of(new OptionSourceUse(sourceName.trim(), valueProperty, labelProperty, qualifier, action.line(), Confidence.LIKELY));
    }

    private Optional<BeanRead> beanRead(JspAction action) {
        if (action.name().equals("bean:write")) {
            return beanRead(action, "bean-write");
        }
        if (action.name().startsWith("logic:")
            && !action.name().equals("logic:redirect")
            && !action.name().equals("logic:forward")
            && !action.name().equals("logic:messagesPresent")
            && !action.name().equals("logic:messagesNotPresent")) {
            return beanRead(action, action.name().replace(':', '-'));
        }
        return Optional.empty();
    }

    private Optional<BeanRead> beanRead(JspAction action, String context) {
        String beanName = action.attributes().get("name");
        if (beanName == null || beanName.isBlank() || isDynamic(beanName)) {
            return Optional.empty();
        }
        String property = action.attributes().get("property");
        if (property != null && isDynamic(property)) {
            return Optional.empty();
        }
        String reference = property == null || property.isBlank() ? beanName.trim() : beanName.trim() + "." + property.trim();
        return Optional.of(new BeanRead(reference, context + ":" + reference, action.line(), Confidence.LIKELY));
    }

    private Optional<TilesJspInclude> tilesJspInclude(JspAction action) {
        if (!action.name().equals("tiles:put") && !action.name().equals("tiles:insert")) {
            return Optional.empty();
        }
        String target = firstNonBlank(action.attributes().get("value"), action.attributes().get("page"));
        if (target == null || isDynamic(target) || !isJspTarget(target)) {
            return Optional.empty();
        }
        String name = action.attributes().get("name");
        String qualifier = action.name() + ":" + (name == null || name.isBlank() ? target : name.trim() + "=" + target);
        return Optional.of(new TilesJspInclude(target.trim(), qualifier, action.line(), Confidence.LIKELY));
    }

    private boolean isJspTarget(String target) {
        String path = targetPath(target);
        return path.endsWith(".jsp") || path.endsWith(".jspx");
    }

    private String targetPath(String target) {
        int queryIndex = target.indexOf('?');
        int hashIndex = target.indexOf('#');
        int end = target.length();
        if (queryIndex >= 0) {
            end = queryIndex;
        }
        if (hashIndex >= 0 && hashIndex < end) {
            end = hashIndex;
        }
        return target.substring(0, end).trim();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null || second.isBlank() ? null : second.trim();
    }

    private boolean isDynamic(String value) {
        return value.contains("<%") || value.contains("${");
    }

    record JspNavigation(String target, String qualifier, int line, Confidence confidence) {
    }

    record LinkParameter(String parameterName, String sourceName, String target, String qualifier, int line, Confidence confidence) {
    }

    record ForwardConfigUse(String forwardName, String qualifier, int line, Confidence confidence) {
    }

    record OptionSourceUse(String sourceName, String valueProperty, String labelProperty, String qualifier, int line, Confidence confidence) {
    }

    record BeanRead(String reference, String qualifier, int line, Confidence confidence) {
    }

    record TilesDefinitionUse(String definition, String qualifier, int line, Confidence confidence) {
    }

    record TilesJspInclude(String target, String qualifier, int line, Confidence confidence) {
    }
}
