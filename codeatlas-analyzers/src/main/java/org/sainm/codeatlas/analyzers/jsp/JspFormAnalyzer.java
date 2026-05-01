package org.sainm.codeatlas.analyzers.jsp;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.analyzers.struts.StrutsConfigAnalyzer;
import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.EvidenceKey;
import org.sainm.codeatlas.graph.model.FactKey;
import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;
import org.sainm.codeatlas.graph.model.GraphNodeFactory;
import org.sainm.codeatlas.graph.model.NodeRole;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.model.SymbolKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class JspFormAnalyzer {
    private final TolerantJspFormExtractor extractor = new TolerantJspFormExtractor();
    private final TolerantJspSemanticExtractor semanticExtractor = new TolerantJspSemanticExtractor();
    private final WebAppContextBuilder webAppContextBuilder = new WebAppContextBuilder();
    private final JspIncludeResolver includeResolver = new JspIncludeResolver();
    private final StrutsJspTagAdapter strutsTagAdapter = new StrutsJspTagAdapter();
    private final SpringJspTagAdapter springTagAdapter = new SpringJspTagAdapter();

    public JspAnalysisResult analyze(AnalyzerScope scope, String projectKey, String sourceRootKey, Path jspFile) {
        try {
            String jspText = Files.readString(jspFile);
            List<JspForm> forms = extractor.extract(jspText);
            List<GraphNode> nodes = new ArrayList<>();
            List<GraphFact> facts = new ArrayList<>();

            SymbolId jspPage = SymbolId.logicalPath(SymbolKind.JSP_PAGE, projectKey, scope.moduleKey(), sourceRootKey, jspFile.toString(), null);
            WebAppContext context = webAppContext(sourceRootKey, jspFile);
            JspSemanticAnalysis semanticAnalysis = semanticExtractor.extract(jspText, context, jspFile);
            nodes.add(GraphNodeFactory.jspNode(jspPage, NodeRole.JSP_ARTIFACT));
            for (int i = 0; i < forms.size(); i++) {
                addForm(scope, projectKey, sourceRootKey, jspFile, jspPage, semanticAnalysis, forms.get(i), i, nodes, facts);
            }
            addTaglibLinks(scope, projectKey, sourceRootKey, jspFile, jspPage, semanticAnalysis, context, nodes, facts);
            addStrutsTilesLinks(scope, projectKey, sourceRootKey, jspFile, jspPage, semanticAnalysis, context, nodes, facts);
            addStrutsForwardConfigLinks(scope, projectKey, sourceRootKey, jspFile, jspPage, semanticAnalysis, nodes, facts);
            addStrutsOptionSourceLinks(scope, projectKey, sourceRootKey, jspFile, jspPage, semanticAnalysis, nodes, facts);
            addSpringOptionSourceLinks(scope, projectKey, sourceRootKey, jspFile, jspPage, semanticAnalysis, nodes, facts);
            addStrutsBeanReadLinks(scope, projectKey, jspFile, jspPage, semanticAnalysis, nodes, facts);
            addElExpressionReadLinks(scope, projectKey, jspFile, jspPage, semanticAnalysis, nodes, facts);
            addScriptletRequestAccessLinks(scope, projectKey, jspFile, jspPage, semanticAnalysis, nodes, facts);
            addIncludeLinks(scope, projectKey, sourceRootKey, jspFile, jspPage, semanticAnalysis, context, nodes, facts);
            addNavigationLinks(scope, projectKey, sourceRootKey, jspFile, jspPage, semanticAnalysis, nodes, facts);
            addStrutsLinkParameterLinks(scope, projectKey, sourceRootKey, jspFile, jspPage, semanticAnalysis, nodes, facts);
            return new JspAnalysisResult(forms, nodes, facts, semanticAnalysis);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to analyze JSP forms: " + jspFile, exception);
        }
    }

    private void addForm(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path jspFile,
        SymbolId jspPage,
        JspSemanticAnalysis semanticAnalysis,
        JspForm form,
        int index,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        SymbolId formSymbol = SymbolId.logicalPath(SymbolKind.JSP_FORM, projectKey, scope.moduleKey(), sourceRootKey, jspFile.toString(), "form:" + index);
        SymbolId actionPath = StrutsConfigAnalyzer.actionPath(projectKey, scope.moduleKey(), sourceRootKey, form.action());
        nodes.add(GraphNodeFactory.jspNode(formSymbol, NodeRole.JSP_ARTIFACT));
        nodes.add(GraphNodeFactory.actionPathNode(actionPath));
        facts.add(fact(scope, jspPage, RelationType.DECLARES, formSymbol, jspFile, form.line(), "jsp-form", jspConfidence(semanticAnalysis, Confidence.CERTAIN)));
        facts.add(fact(scope, formSymbol, RelationType.SUBMITS_TO, actionPath, jspFile, form.line(), "form-action", Confidence.LIKELY));

        for (JspInput input : form.inputs()) {
            SymbolId inputSymbol = SymbolId.logicalPath(
                SymbolKind.JSP_INPUT,
                projectKey,
                scope.moduleKey(),
                sourceRootKey,
                jspFile.toString(),
                "form:" + index + ":input:" + input.name()
            );
            SymbolId parameter = SymbolId.logicalPath(
                SymbolKind.REQUEST_PARAMETER,
                projectKey,
                scope.moduleKey(),
                "_request",
                input.name(),
                null
            );
            nodes.add(GraphNodeFactory.jspNode(inputSymbol, NodeRole.JSP_ARTIFACT));
            nodes.add(GraphNodeFactory.requestParameterNode(parameter));
            facts.add(fact(scope, formSymbol, RelationType.DECLARES, inputSymbol, jspFile, input.line(), "jsp-input:" + input.name(), jspConfidence(semanticAnalysis, Confidence.CERTAIN)));
            facts.add(fact(scope, inputSymbol, RelationType.WRITES_PARAM, actionPath, jspFile, input.line(), input.name(), Confidence.LIKELY));
            facts.add(fact(scope, inputSymbol, RelationType.WRITES_PARAM, parameter, jspFile, input.line(), input.name(), Confidence.LIKELY));
        }
    }

    private void addScriptletRequestAccessLinks(
        AnalyzerScope scope,
        String projectKey,
        Path jspFile,
        SymbolId jspPage,
        JspSemanticAnalysis semanticAnalysis,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        for (JspExpressionFragment expression : semanticAnalysis.expressions()) {
            if (!expression.kind().equals("SCRIPTLET") && !expression.kind().equals("EXPRESSION")) {
                continue;
            }
            for (RequestAccess access : requestAccesses(expression.expression())) {
                RelationType relationType = access.methodName().equals("setAttribute")
                    ? RelationType.WRITES_PARAM
                    : RelationType.READS_PARAM;
                SymbolId parameter = SymbolId.logicalPath(
                    SymbolKind.REQUEST_PARAMETER,
                    projectKey,
                    scope.moduleKey(),
                    "_request",
                    access.parameterName(),
                    null
                );
                nodes.add(GraphNodeFactory.requestParameterNode(parameter));
                facts.add(fact(
                    scope,
                    jspPage,
                    relationType,
                    parameter,
                    jspFile,
                    expression.line(),
                    "jsp-scriptlet-" + access.methodName() + ":" + access.parameterName(),
                    Confidence.LIKELY
                ));
            }
        }
    }

    private void addTaglibLinks(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path jspFile,
        SymbolId jspPage,
        JspSemanticAnalysis semanticAnalysis,
        WebAppContext context,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        for (JspTaglibReference taglib : semanticAnalysis.taglibs()) {
            SymbolId taglibConfig = configSymbol(projectKey, scope.moduleKey(), sourceRootKey, jspFile, "jsp-taglib:" + taglib.prefix());
            nodes.add(GraphNodeFactory.configNode(taglibConfig));
            facts.add(fact(
                scope,
                jspPage,
                RelationType.USES_CONFIG,
                taglibConfig,
                jspFile,
                taglib.line(),
                taglibQualifier(taglib),
                taglib.confidence()
            ));
        }
        for (JspAction action : semanticAnalysis.actions()) {
            tagFileLocation(action, semanticAnalysis, context).ifPresent(location -> {
                SymbolId tagFileConfig = configSymbol(projectKey, scope.moduleKey(), sourceRootKey, jspFile, "jsp-tag-file:" + location);
                nodes.add(GraphNodeFactory.configNode(tagFileConfig));
                facts.add(fact(
                    scope,
                    jspPage,
                    RelationType.USES_CONFIG,
                    tagFileConfig,
                    jspFile,
                    action.line(),
                    "jsp-custom-tag:" + action.name() + "->" + location,
                    jspConfidence(semanticAnalysis, Confidence.CERTAIN)
                ));
            });
        }
    }

    private java.util.Optional<String> tagFileLocation(JspAction action, JspSemanticAnalysis analysis, WebAppContext context) {
        int separator = action.name().indexOf(':');
        if (separator <= 0 || separator == action.name().length() - 1) {
            return java.util.Optional.empty();
        }
        String prefix = action.name().substring(0, separator);
        String localName = action.name().substring(separator + 1);
        return analysis.taglibs().stream()
            .filter(taglib -> taglib.prefix().equals(prefix) && taglib.tagdir() != null)
            .map(taglib -> tagFileKey(taglib.tagdir(), localName))
            .map(context.tagFiles()::get)
            .filter(location -> location != null && !location.isBlank())
            .findFirst();
    }

    private String tagFileKey(String tagdir, String localName) {
        String normalized = tagdir.replace('\\', '/');
        if (normalized.startsWith("/WEB-INF/tags/")) {
            normalized = normalized.substring("/WEB-INF/tags/".length());
        } else if (normalized.startsWith("WEB-INF/tags/")) {
            normalized = normalized.substring("WEB-INF/tags/".length());
        }
        return normalized + "/" + localName;
    }

    private String taglibQualifier(JspTaglibReference taglib) {
        return "jsp-taglib:" + taglib.prefix() + "=" + firstNonBlank(firstNonBlank(taglib.uri(), taglib.tagdir()), taglib.resolvedLocation());
    }

    private SymbolId configSymbol(String projectKey, String moduleKey, String sourceRootKey, Path jspFile, String localId) {
        return SymbolId.logicalPath(SymbolKind.CONFIG_KEY, projectKey, moduleKey, sourceRootKey, jspFile.toString(), localId);
    }

    private void addStrutsTilesLinks(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path jspFile,
        SymbolId jspPage,
        JspSemanticAnalysis semanticAnalysis,
        WebAppContext context,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        for (StrutsJspTagAdapter.TilesDefinitionUse definitionUse : strutsTagAdapter.tilesDefinitionUses(semanticAnalysis)) {
            SymbolId definition = SymbolId.logicalPath(
                SymbolKind.CONFIG_KEY,
                projectKey,
                scope.moduleKey(),
                sourceRootKey,
                "tiles-definition",
                definitionUse.definition()
            );
            nodes.add(GraphNodeFactory.configNode(definition));
            facts.add(fact(
                scope,
                jspPage,
                RelationType.USES_CONFIG,
                definition,
                jspFile,
                definitionUse.line(),
                definitionUse.qualifier(),
                definitionUse.confidence()
            ));
        }
        for (StrutsJspTagAdapter.TilesJspInclude include : strutsTagAdapter.tilesJspIncludes(semanticAnalysis)) {
            SymbolId includedPage = SymbolId.logicalPath(
                SymbolKind.JSP_PAGE,
                projectKey,
                scope.moduleKey(),
                sourceRootKey,
                tilesJspPath(context, include.target()).toString(),
                null
            );
            nodes.add(GraphNodeFactory.jspNode(includedPage, NodeRole.JSP_ARTIFACT));
            facts.add(fact(
                scope,
                jspPage,
                RelationType.INCLUDES,
                includedPage,
                jspFile,
                include.line(),
                include.qualifier(),
                include.confidence()
            ));
        }
    }

    private void addStrutsForwardConfigLinks(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path jspFile,
        SymbolId jspPage,
        JspSemanticAnalysis semanticAnalysis,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        for (StrutsJspTagAdapter.ForwardConfigUse forwardUse : strutsTagAdapter.forwardConfigUses(semanticAnalysis)) {
            SymbolId forwardConfig = SymbolId.logicalPath(
                SymbolKind.CONFIG_KEY,
                projectKey,
                scope.moduleKey(),
                sourceRootKey,
                "struts-forward",
                forwardUse.forwardName()
            );
            nodes.add(GraphNodeFactory.configNode(forwardConfig));
            facts.add(fact(
                scope,
                jspPage,
                RelationType.USES_CONFIG,
                forwardConfig,
                jspFile,
                forwardUse.line(),
                forwardUse.qualifier(),
                forwardUse.confidence()
            ));
        }
    }

    private void addStrutsOptionSourceLinks(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path jspFile,
        SymbolId jspPage,
        JspSemanticAnalysis semanticAnalysis,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        for (StrutsJspTagAdapter.OptionSourceUse optionSource : strutsTagAdapter.optionSourceUses(semanticAnalysis)) {
            SymbolId sourceConfig = SymbolId.logicalPath(
                SymbolKind.CONFIG_KEY,
                projectKey,
                scope.moduleKey(),
                sourceRootKey,
                "jsp-option-source",
                optionSource.sourceName()
            );
            nodes.add(GraphNodeFactory.configNode(sourceConfig));
            facts.add(fact(
                scope,
                jspPage,
                RelationType.USES_CONFIG,
                sourceConfig,
                jspFile,
                optionSource.line(),
                optionSource.qualifier(),
                optionSource.confidence()
            ));
        }
    }

    private void addSpringOptionSourceLinks(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path jspFile,
        SymbolId jspPage,
        JspSemanticAnalysis semanticAnalysis,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        for (SpringJspTagAdapter.OptionSourceUse optionSource : springTagAdapter.optionSourceUses(semanticAnalysis)) {
            SymbolId sourceConfig = SymbolId.logicalPath(
                SymbolKind.CONFIG_KEY,
                projectKey,
                scope.moduleKey(),
                sourceRootKey,
                "spring-option-source",
                optionSource.sourceName()
            );
            nodes.add(GraphNodeFactory.configNode(sourceConfig));
            facts.add(fact(
                scope,
                jspPage,
                RelationType.USES_CONFIG,
                sourceConfig,
                jspFile,
                optionSource.line(),
                optionSource.qualifier(),
                optionSource.confidence()
            ));
        }
    }

    private void addStrutsBeanReadLinks(
        AnalyzerScope scope,
        String projectKey,
        Path jspFile,
        SymbolId jspPage,
        JspSemanticAnalysis semanticAnalysis,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        for (StrutsJspTagAdapter.BeanRead beanRead : strutsTagAdapter.beanReads(semanticAnalysis)) {
            SymbolId beanReference = SymbolId.logicalPath(
                SymbolKind.REQUEST_PARAMETER,
                projectKey,
                scope.moduleKey(),
                "_request",
                beanRead.reference(),
                null
            );
            nodes.add(GraphNodeFactory.requestParameterNode(beanReference));
            facts.add(fact(
                scope,
                jspPage,
                RelationType.READS_PARAM,
                beanReference,
                jspFile,
                beanRead.line(),
                beanRead.qualifier(),
                beanRead.confidence()
            ));
        }
    }

    private void addElExpressionReadLinks(
        AnalyzerScope scope,
        String projectKey,
        Path jspFile,
        SymbolId jspPage,
        JspSemanticAnalysis semanticAnalysis,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        for (JspExpressionFragment expression : semanticAnalysis.expressions()) {
            if (!expression.kind().equals("EL")) {
                continue;
            }
            for (String reference : elReferences(expression.expression())) {
                SymbolId parameter = SymbolId.logicalPath(
                    SymbolKind.REQUEST_PARAMETER,
                    projectKey,
                    scope.moduleKey(),
                    "_request",
                    reference,
                    null
                );
                nodes.add(GraphNodeFactory.requestParameterNode(parameter));
                facts.add(fact(
                    scope,
                    jspPage,
                    RelationType.READS_PARAM,
                    parameter,
                    jspFile,
                    expression.line(),
                    "jsp-el:" + reference,
                    Confidence.POSSIBLE
                ));
            }
        }
    }

    private Path tilesJspPath(WebAppContext context, String target) {
        String path = targetPath(target);
        if (path.startsWith("/")) {
            return context.webRoot().resolve(path.substring(1)).normalize();
        }
        return context.webRoot().resolve(path).normalize();
    }

    private void addNavigationLinks(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path jspFile,
        SymbolId jspPage,
        JspSemanticAnalysis semanticAnalysis,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        for (StrutsJspTagAdapter.JspNavigation navigation : strutsTagAdapter.navigations(semanticAnalysis)) {
            SymbolId target = navigationTarget(projectKey, scope.moduleKey(), sourceRootKey, navigation.target());
            if (target == null) {
                continue;
            }
            addNavigationTargetNode(nodes, target);
            facts.add(fact(scope, jspPage, RelationType.FORWARDS_TO, target, jspFile, navigation.line(), navigation.qualifier(), navigation.confidence()));
        }
        for (JspAction action : semanticAnalysis.actions()) {
            if (!action.name().equals("jsp:forward")) {
                continue;
            }
            String targetValue = action.attributes().get("page");
            if (targetValue == null || isDynamic(targetValue)) {
                continue;
            }
            SymbolId target = navigationTarget(projectKey, scope.moduleKey(), sourceRootKey, targetValue);
            if (target == null) {
                continue;
            }
            addNavigationTargetNode(nodes, target);
            facts.add(fact(scope, jspPage, RelationType.FORWARDS_TO, target, jspFile, action.line(), "jsp:forward:" + targetValue, Confidence.LIKELY));
        }
    }

    private void addStrutsLinkParameterLinks(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path jspFile,
        SymbolId jspPage,
        JspSemanticAnalysis semanticAnalysis,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        int index = 0;
        for (StrutsJspTagAdapter.LinkParameter linkParameter : strutsTagAdapter.linkParameters(semanticAnalysis)) {
            SymbolId linkParameterSymbol = SymbolId.logicalPath(
                SymbolKind.JSP_INPUT,
                projectKey,
                scope.moduleKey(),
                sourceRootKey,
                jspFile.toString(),
                "link:" + index + ":param:" + linkParameter.parameterName()
            );
            SymbolId parameter = SymbolId.logicalPath(
                SymbolKind.REQUEST_PARAMETER,
                projectKey,
                scope.moduleKey(),
                "_request",
                linkParameter.parameterName(),
                null
            );
            SymbolId target = navigationTarget(projectKey, scope.moduleKey(), sourceRootKey, linkParameter.target());
            nodes.add(GraphNodeFactory.jspNode(linkParameterSymbol, NodeRole.JSP_ARTIFACT));
            nodes.add(GraphNodeFactory.requestParameterNode(parameter));
            facts.add(fact(scope, jspPage, RelationType.DECLARES, linkParameterSymbol, jspFile, linkParameter.line(), linkParameter.qualifier(), linkParameter.confidence()));
            facts.add(fact(scope, linkParameterSymbol, RelationType.WRITES_PARAM, parameter, jspFile, linkParameter.line(), linkParameter.parameterName(), linkParameter.confidence()));
            if (target != null) {
                addNavigationTargetNode(nodes, target);
                facts.add(fact(scope, linkParameterSymbol, RelationType.WRITES_PARAM, target, jspFile, linkParameter.line(), linkParameter.qualifier(), linkParameter.confidence()));
            }
            index++;
        }
    }

    private void addIncludeLinks(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path jspFile,
        SymbolId jspPage,
        JspSemanticAnalysis semanticAnalysis,
        WebAppContext context,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        for (JspIncludeReference include : includeResolver.resolve(jspFile, semanticAnalysis, context)) {
            if (isDynamic(include.rawPath())) {
                continue;
            }
            SymbolId includedPage = SymbolId.logicalPath(
                SymbolKind.JSP_PAGE,
                projectKey,
                scope.moduleKey(),
                sourceRootKey,
                include.resolvedPath().toString(),
                null
            );
            nodes.add(GraphNodeFactory.jspNode(includedPage, NodeRole.JSP_ARTIFACT));
            facts.add(fact(
                scope,
                jspPage,
                RelationType.INCLUDES,
                includedPage,
                jspFile,
                include.line(),
                include.type().name().toLowerCase(java.util.Locale.ROOT) + ":" + include.rawPath(),
                include.confidence()
            ));
        }
    }

    private SymbolId navigationTarget(String projectKey, String moduleKey, String sourceRootKey, String target) {
        String path = targetPath(target);
        if (path.endsWith(".do") || (!path.contains(".") && path.startsWith("/"))) {
            return StrutsConfigAnalyzer.actionPath(projectKey, moduleKey, sourceRootKey, path);
        }
        if (path.endsWith(".jsp") || path.endsWith(".jspx")) {
            return SymbolId.logicalPath(SymbolKind.JSP_PAGE, projectKey, moduleKey, sourceRootKey, path, null);
        }
        return null;
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

    private List<RequestAccess> requestAccesses(String text) {
        List<RequestAccess> accesses = new ArrayList<>();
        int offset = 0;
        while (offset < text.length()) {
            int requestStart = text.indexOf("request", offset);
            if (requestStart < 0) {
                break;
            }
            int cursor = requestStart + "request".length();
            if (isIdentifierBoundary(text, requestStart - 1) && isIdentifierBoundary(text, cursor)) {
                cursor = JspTextScanner.skipWhitespace(text, cursor);
                if (cursor < text.length() && text.charAt(cursor) == '.') {
                    cursor = JspTextScanner.skipWhitespace(text, cursor + 1);
                    int methodEnd = JspTextScanner.readIdentifierEnd(text, cursor);
                    String methodName = text.substring(cursor, methodEnd);
                    if (isRequestAccessMethod(methodName)) {
                        cursor = JspTextScanner.skipWhitespace(text, methodEnd);
                        if (cursor < text.length() && text.charAt(cursor) == '(') {
                            cursor = JspTextScanner.skipWhitespace(text, cursor + 1);
                            JspTextScanner.QuotedValue value = JspTextScanner.readQuotedValue(text, cursor);
                            if (value != null && !value.value().isBlank()) {
                                accesses.add(new RequestAccess(methodName, value.value()));
                                offset = value.nextOffset();
                                continue;
                            }
                        }
                    }
                }
            }
            offset = requestStart + "request".length();
        }
        return accesses;
    }

    private List<String> elReferences(String text) {
        Set<String> references = new LinkedHashSet<>();
        int offset = 0;
        while (offset < text.length()) {
            char current = text.charAt(offset);
            if (current == '"' || current == '\'') {
                JspTextScanner.QuotedValue quoted = JspTextScanner.readQuotedValue(text, offset);
                offset = quoted == null ? offset + 1 : quoted.nextOffset();
                continue;
            }
            if (!isIdentifierStart(current)) {
                offset++;
                continue;
            }
            int start = offset;
            int end = JspTextScanner.readIdentifierEnd(text, offset);
            String first = text.substring(start, end);
            int cursor = JspTextScanner.skipWhitespace(text, end);
            if (isElKeyword(first) || (cursor < text.length() && text.charAt(cursor) == ':')) {
                offset = end;
                continue;
            }
            if (cursor < text.length() && text.charAt(cursor) == '(') {
                offset = end;
                continue;
            }
            StringBuilder reference = new StringBuilder(first);
            boolean hasProperty = false;
            while (cursor < text.length() && text.charAt(cursor) == '.') {
                cursor = JspTextScanner.skipWhitespace(text, cursor + 1);
                if (cursor >= text.length() || !isIdentifierStart(text.charAt(cursor))) {
                    break;
                }
                int partEnd = JspTextScanner.readIdentifierEnd(text, cursor);
                reference.append('.').append(text, cursor, partEnd);
                hasProperty = true;
                cursor = JspTextScanner.skipWhitespace(text, partEnd);
            }
            if (hasProperty || isLikelySingleElReference(first)) {
                String normalized = normalizeElReference(reference.toString());
                if (normalized != null) {
                    references.add(normalized);
                }
            }
            offset = Math.max(cursor, end);
        }
        return List.copyOf(references);
    }

    private boolean isIdentifierStart(char value) {
        return Character.isLetter(value) || value == '_' || value == '$';
    }

    private boolean isElKeyword(String value) {
        return switch (value) {
            case "and", "or", "not", "empty", "eq", "ne", "lt", "gt", "le", "ge", "div", "mod", "true", "false", "null", "instanceof" -> true;
            default -> false;
        };
    }

    private boolean isLikelySingleElReference(String value) {
        return !value.isBlank() && !isElKeyword(value);
    }

    private String normalizeElReference(String reference) {
        int separator = reference.indexOf('.');
        if (separator <= 0) {
            return isElImplicitObject(reference) ? null : reference;
        }
        String root = reference.substring(0, separator);
        String tail = reference.substring(separator + 1);
        if (isScopedElObject(root)) {
            return tail.isBlank() ? null : tail;
        }
        if (isElImplicitObject(root)) {
            return null;
        }
        return reference;
    }

    private boolean isScopedElObject(String value) {
        return value.equals("pageScope")
            || value.equals("requestScope")
            || value.equals("sessionScope")
            || value.equals("applicationScope");
    }

    private boolean isElImplicitObject(String value) {
        return isScopedElObject(value)
            || value.equals("param")
            || value.equals("paramValues")
            || value.equals("header")
            || value.equals("headerValues")
            || value.equals("cookie")
            || value.equals("initParam")
            || value.equals("pageContext");
    }

    private boolean isRequestAccessMethod(String methodName) {
        return methodName.equals("getParameter") || methodName.equals("getAttribute") || methodName.equals("setAttribute");
    }

    private boolean isIdentifierBoundary(String text, int offset) {
        if (offset < 0 || offset >= text.length()) {
            return true;
        }
        char c = text.charAt(offset);
        return !Character.isLetterOrDigit(c) && c != '_' && c != '$';
    }

    private record RequestAccess(String methodName, String parameterName) {
    }

    private void addNavigationTargetNode(List<GraphNode> nodes, SymbolId target) {
        if (target.kind() == SymbolKind.ACTION_PATH) {
            nodes.add(GraphNodeFactory.actionPathNode(target));
        } else if (target.kind() == SymbolKind.JSP_PAGE) {
            nodes.add(GraphNodeFactory.jspNode(target, NodeRole.JSP_ARTIFACT));
        }
    }

    private WebAppContext webAppContext(String sourceRootKey, Path jspFile) {
        Path webRoot = webRoot(sourceRootKey, jspFile);
        return webAppContextBuilder.build(webRoot);
    }

    private Path webRoot(String sourceRootKey, Path jspFile) {
        Path current = jspFile.toAbsolutePath().normalize().getParent();
        String normalizedRootKey = sourceRootKey == null ? "" : sourceRootKey.replace('\\', '/');
        while (current != null) {
            String currentPath = current.toString().replace('\\', '/');
            if (!normalizedRootKey.isBlank() && currentPath.endsWith(normalizedRootKey)) {
                return current;
            }
            if (Files.exists(current.resolve("WEB-INF"))) {
                return current;
            }
            current = current.getParent();
        }
        return jspFile.toAbsolutePath().normalize().getParent();
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

    private Confidence jspConfidence(JspSemanticAnalysis semanticAnalysis, Confidence confidence) {
        if (semanticAnalysis != null
            && (semanticAnalysis.parserSource() == JspSemanticParserSource.TOKENIZER_FALLBACK
                || semanticAnalysis.parserSource() == JspSemanticParserSource.JERICHO_WITH_TOKENIZER_MERGE)
            && confidence == Confidence.CERTAIN) {
            return Confidence.LIKELY;
        }
        return confidence;
    }

    private GraphFact fact(
        AnalyzerScope scope,
        SymbolId source,
        RelationType relationType,
        SymbolId target,
        Path jspFile,
        int line,
        String qualifier,
        Confidence confidence
    ) {
        return GraphFact.active(
            new FactKey(source, relationType, target, qualifier),
            new EvidenceKey(SourceType.JSP_FALLBACK, "tolerant-jsp-form", jspFile.toString(), line, line, qualifier),
            scope.projectId(),
            scope.snapshotId(),
            scope.analysisRunId(),
            scope.scopeKey(),
            confidence,
            SourceType.JSP_FALLBACK
        );
    }
}
