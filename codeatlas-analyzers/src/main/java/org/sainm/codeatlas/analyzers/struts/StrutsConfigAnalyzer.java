package org.sainm.codeatlas.analyzers.struts;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.analyzers.xml.SafeXmlDocumentLoader;
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public final class StrutsConfigAnalyzer {
    private final SafeXmlDocumentLoader xmlLoader = new SafeXmlDocumentLoader();

    public StrutsConfigAnalysisResult analyze(AnalyzerScope scope, String projectKey, String sourceRootKey, Path configXml) {
        return analyze(scope, projectKey, sourceRootKey, configXml, "");
    }

    public StrutsConfigAnalysisResult analyze(AnalyzerScope scope, String projectKey, String sourceRootKey, Path configXml, String modulePrefix) {
        try {
            Document document = xmlLoader.load(configXml);
            List<StrutsFormBean> forms = formBeans(document);
            Map<String, StrutsFormBean> formsByName = new HashMap<>();
            forms.forEach(form -> formsByName.put(form.name(), form));

            List<StrutsActionMapping> actions = new ArrayList<>();
            List<StrutsForward> forwards = new ArrayList<>();
            List<StrutsPlugin> plugins = new ArrayList<>();
            List<StrutsControllerConfig> controllers = new ArrayList<>();
            List<StrutsMessageResource> messageResources = new ArrayList<>();
            List<StrutsExceptionMapping> exceptions = new ArrayList<>();
            List<GraphNode> nodes = new ArrayList<>();
            List<GraphFact> facts = new ArrayList<>();
            addControllers(scope, projectKey, sourceRootKey, configXml, document, controllers, nodes, facts);
            addActionMappingsConfig(scope, projectKey, sourceRootKey, configXml, document, nodes, facts);
            addGlobalForwards(scope, projectKey, sourceRootKey, configXml, modulePrefix, document, forwards, nodes, facts);
            addMessageResources(scope, projectKey, sourceRootKey, configXml, document, messageResources, nodes, facts);
            addGlobalExceptions(scope, projectKey, sourceRootKey, configXml, modulePrefix, document, exceptions, nodes, facts);
            NodeList actionNodes = document.getElementsByTagName("action");
            for (int i = 0; i < actionNodes.getLength(); i++) {
                Element action = (Element) actionNodes.item(i);
                StrutsActionMapping mapping = new StrutsActionMapping(
                    action.getAttribute("path"),
                    action.getAttribute("type"),
                    action.getAttribute("name"),
                    action.getAttribute("scope"),
                    action.getAttribute("input"),
                    action.getAttribute("parameter"),
                    action.getAttribute("forward"),
                    action.getAttribute("className")
                );
                actions.add(mapping);
                String actionPath = moduleActionPath(modulePrefix, mapping.path());
                addActionFacts(scope, projectKey, sourceRootKey, configXml, modulePrefix, actionPath, mapping, formsByName, nodes, facts);
                addForwards(scope, projectKey, sourceRootKey, configXml, modulePrefix, actionPath, action, forwards, nodes, facts);
                addActionExceptions(scope, projectKey, sourceRootKey, configXml, modulePrefix, actionPath, action, exceptions, nodes, facts);
            }
            addPlugins(scope, projectKey, sourceRootKey, configXml, document, plugins, nodes, facts);

            return new StrutsConfigAnalysisResult(forms, actions, forwards, plugins, controllers, messageResources, exceptions, nodes, facts);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Failed to analyze Struts config: " + configXml, exception);
        }
    }

    private List<StrutsFormBean> formBeans(Document document) {
        List<StrutsFormBean> forms = new ArrayList<>();
        NodeList formNodes = document.getElementsByTagName("form-bean");
        for (int i = 0; i < formNodes.getLength(); i++) {
            Element form = (Element) formNodes.item(i);
            forms.add(new StrutsFormBean(form.getAttribute("name"), form.getAttribute("type"), formProperties(form)));
        }
        return forms;
    }

    private List<StrutsFormProperty> formProperties(Element form) {
        List<StrutsFormProperty> properties = new ArrayList<>();
        NodeList propertyNodes = form.getElementsByTagName("form-property");
        for (int i = 0; i < propertyNodes.getLength(); i++) {
            Element property = (Element) propertyNodes.item(i);
            String name = property.getAttribute("name");
            if (name == null || name.isBlank()) {
                continue;
            }
            properties.add(new StrutsFormProperty(name, property.getAttribute("type"), property.getAttribute("initial")));
        }
        return properties;
    }

    private void addActionFacts(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path configXml,
        String modulePrefix,
        String actionPathValue,
        StrutsActionMapping mapping,
        Map<String, StrutsFormBean> formsByName,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        SymbolId actionPath = actionPath(projectKey, scope.moduleKey(), sourceRootKey, actionPathValue);
        nodes.add(GraphNodeFactory.actionPathNode(actionPath));
        if (mapping.type() != null) {
            SymbolId actionClass = SymbolId.classSymbol(projectKey, scope.moduleKey(), sourceRootKey, mapping.type());
            nodes.add(GraphNodeFactory.classNode(actionClass, NodeRole.STRUTS_ACTION));
            facts.add(fact(scope, actionPath, RelationType.ROUTES_TO, actionClass, configXml, "struts-action", Confidence.CERTAIN));
        }

        if (mapping.name() != null && formsByName.containsKey(mapping.name())) {
            StrutsFormBean formBean = formsByName.get(mapping.name());
            SymbolId formClass = SymbolId.classSymbol(projectKey, scope.moduleKey(), sourceRootKey, formBean.type());
            nodes.add(GraphNodeFactory.classNode(formClass, NodeRole.CODE_TYPE));
            facts.add(fact(scope, actionPath, RelationType.BINDS_TO, formClass, configXml, "action-form:" + mapping.name(), Confidence.CERTAIN));
            addFormPropertyFacts(scope, projectKey, sourceRootKey, configXml, formBean, formClass, nodes, facts);
        }
        if (mapping.input() != null) {
            SymbolId inputJsp = SymbolId.logicalPath(SymbolKind.JSP_PAGE, projectKey, scope.moduleKey(), sourceRootKey, mapping.input(), null);
            nodes.add(GraphNodeFactory.jspNode(inputJsp, NodeRole.JSP_ARTIFACT));
            facts.add(fact(scope, actionPath, RelationType.FORWARDS_TO, inputJsp, configXml, "input", Confidence.LIKELY));
        }
        if (mapping.parameter() != null) {
            SymbolId dispatchParameter = SymbolId.logicalPath(
                SymbolKind.REQUEST_PARAMETER,
                projectKey,
                scope.moduleKey(),
                "_request",
                mapping.parameter(),
                null
            );
            nodes.add(GraphNodeFactory.requestParameterNode(dispatchParameter));
            facts.add(fact(scope, actionPath, RelationType.READS_PARAM, dispatchParameter, configXml, "dispatch-parameter:" + mapping.parameter(), Confidence.CERTAIN));
        }
        if (mapping.forward() != null) {
            SymbolId target = jspOrActionTarget(projectKey, scope.moduleKey(), sourceRootKey, configXml, modulePrefix, mapping.forward());
            addTargetNode(nodes, target);
            facts.add(fact(scope, actionPath, RelationType.FORWARDS_TO, target, configXml, "action-forward", Confidence.CERTAIN));
        }
        if (mapping.className() != null) {
            SymbolId mappingClass = SymbolId.classSymbol(projectKey, scope.moduleKey(), sourceRootKey, mapping.className());
            nodes.add(GraphNodeFactory.classNode(mappingClass, NodeRole.CODE_TYPE));
            facts.add(fact(scope, actionPath, RelationType.USES_CONFIG, mappingClass, configXml, "action-mapping-className", Confidence.CERTAIN));
        }
    }

    private void addFormPropertyFacts(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path configXml,
        StrutsFormBean formBean,
        SymbolId formClass,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        for (StrutsFormProperty property : formBean.properties()) {
            SymbolId propertyConfig = configSymbol(
                projectKey,
                scope.moduleKey(),
                sourceRootKey,
                configXml,
                "form-bean:" + formBean.name() + ":property:" + property.name()
            );
            SymbolId parameter = SymbolId.logicalPath(
                SymbolKind.REQUEST_PARAMETER,
                projectKey,
                scope.moduleKey(),
                "_request",
                property.name(),
                null
            );
            nodes.add(GraphNodeFactory.configNode(propertyConfig));
            nodes.add(GraphNodeFactory.requestParameterNode(parameter));
            facts.add(fact(scope, formClass, RelationType.DECLARES, propertyConfig, configXml, "form-property:" + property.name(), Confidence.CERTAIN));
            facts.add(fact(scope, parameter, RelationType.BINDS_TO, propertyConfig, configXml, property.name(), Confidence.CERTAIN));
        }
    }

    private void addForwards(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path configXml,
        String modulePrefix,
        String actionPathValue,
        Element action,
        List<StrutsForward> forwards,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        SymbolId actionPath = actionPath(projectKey, scope.moduleKey(), sourceRootKey, actionPathValue);
        NodeList forwardNodes = action.getElementsByTagName("forward");
        for (int i = 0; i < forwardNodes.getLength(); i++) {
            Element forward = (Element) forwardNodes.item(i);
            StrutsForward strutsForward = new StrutsForward(actionPathValue, forward.getAttribute("name"), forward.getAttribute("path"));
            forwards.add(strutsForward);
            SymbolId target = jspOrActionTarget(projectKey, scope.moduleKey(), sourceRootKey, configXml, modulePrefix, strutsForward.path());
            SymbolId forwardConfig = forwardConfigSymbol(projectKey, scope.moduleKey(), sourceRootKey, strutsForward.name());
            nodes.add(GraphNodeFactory.configNode(forwardConfig));
            addTargetNode(nodes, target);
            facts.add(fact(scope, actionPath, RelationType.USES_CONFIG, forwardConfig, configXml, "forward-config:" + strutsForward.name(), Confidence.CERTAIN));
            facts.add(fact(scope, forwardConfig, RelationType.FORWARDS_TO, target, configXml, "forward:" + strutsForward.name(), Confidence.CERTAIN));
            facts.add(fact(scope, actionPath, RelationType.FORWARDS_TO, target, configXml, "forward:" + strutsForward.name(), Confidence.CERTAIN));
        }
    }

    private void addGlobalForwards(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path configXml,
        String modulePrefix,
        Document document,
        List<StrutsForward> forwards,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        NodeList globalForwards = document.getElementsByTagName("global-forwards");
        for (int i = 0; i < globalForwards.getLength(); i++) {
            Element global = (Element) globalForwards.item(i);
            NodeList forwardNodes = global.getElementsByTagName("forward");
            SymbolId globalConfig = configSymbol(projectKey, scope.moduleKey(), sourceRootKey, configXml, "struts-global-forwards:" + i);
            nodes.add(GraphNodeFactory.configNode(globalConfig));
            for (int j = 0; j < forwardNodes.getLength(); j++) {
                Element forward = (Element) forwardNodes.item(j);
                StrutsForward strutsForward = new StrutsForward("_global", forward.getAttribute("name"), forward.getAttribute("path"));
                forwards.add(strutsForward);
                SymbolId target = jspOrActionTarget(projectKey, scope.moduleKey(), sourceRootKey, configXml, modulePrefix, strutsForward.path());
                SymbolId forwardConfig = forwardConfigSymbol(projectKey, scope.moduleKey(), sourceRootKey, strutsForward.name());
                nodes.add(GraphNodeFactory.configNode(forwardConfig));
                addTargetNode(nodes, target);
                facts.add(fact(scope, globalConfig, RelationType.USES_CONFIG, forwardConfig, configXml, "global-forward-config:" + strutsForward.name(), Confidence.CERTAIN));
                facts.add(fact(scope, forwardConfig, RelationType.FORWARDS_TO, target, configXml, "global-forward:" + strutsForward.name(), Confidence.CERTAIN));
                facts.add(fact(scope, globalConfig, RelationType.FORWARDS_TO, target, configXml, "global-forward:" + strutsForward.name(), Confidence.CERTAIN));
            }
        }
    }

    private void addMessageResources(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path configXml,
        Document document,
        List<StrutsMessageResource> messageResources,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        NodeList resources = document.getElementsByTagName("message-resources");
        for (int i = 0; i < resources.getLength(); i++) {
            Element resource = (Element) resources.item(i);
            String parameter = resource.getAttribute("parameter");
            if (parameter == null || parameter.isBlank()) {
                continue;
            }
            StrutsMessageResource messageResource = new StrutsMessageResource(
                parameter,
                resource.getAttribute("key"),
                resource.getAttribute("factory"),
                Boolean.parseBoolean(resource.getAttribute("null"))
            );
            messageResources.add(messageResource);
            SymbolId resourceConfig = configSymbol(projectKey, scope.moduleKey(), sourceRootKey, configXml, "struts-message-resource:" + i);
            SymbolId bundleConfig = configSymbol(projectKey, scope.moduleKey(), sourceRootKey, configXml, "message-bundle:" + parameter);
            nodes.add(GraphNodeFactory.configNode(resourceConfig));
            nodes.add(GraphNodeFactory.configNode(bundleConfig));
            facts.add(fact(scope, resourceConfig, RelationType.USES_CONFIG, bundleConfig, configXml, "message-resources:" + parameter, Confidence.CERTAIN));
            if (messageResource.factory() != null) {
                SymbolId factoryClass = SymbolId.classSymbol(projectKey, scope.moduleKey(), sourceRootKey, messageResource.factory());
                nodes.add(GraphNodeFactory.classNode(factoryClass, NodeRole.CODE_TYPE));
                facts.add(fact(scope, resourceConfig, RelationType.USES_CONFIG, factoryClass, configXml, "message-resources-factory", Confidence.CERTAIN));
            }
        }
    }

    private void addGlobalExceptions(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path configXml,
        String modulePrefix,
        Document document,
        List<StrutsExceptionMapping> exceptions,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        NodeList globalExceptions = document.getElementsByTagName("global-exceptions");
        for (int i = 0; i < globalExceptions.getLength(); i++) {
            Element global = (Element) globalExceptions.item(i);
            addExceptionNodes(scope, projectKey, sourceRootKey, configXml, modulePrefix, "_global", global, exceptions, nodes, facts);
        }
    }

    private void addActionExceptions(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path configXml,
        String modulePrefix,
        String actionPath,
        Element action,
        List<StrutsExceptionMapping> exceptions,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        addExceptionNodes(scope, projectKey, sourceRootKey, configXml, modulePrefix, actionPath, action, exceptions, nodes, facts);
    }

    private void addExceptionNodes(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path configXml,
        String modulePrefix,
        String scopeValue,
        Element parent,
        List<StrutsExceptionMapping> exceptions,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        NodeList exceptionNodes = parent.getElementsByTagName("exception");
        for (int i = 0; i < exceptionNodes.getLength(); i++) {
            Element exception = (Element) exceptionNodes.item(i);
            String type = exception.getAttribute("type");
            if (type == null || type.isBlank()) {
                continue;
            }
            StrutsExceptionMapping mapping = new StrutsExceptionMapping(
                scopeValue,
                type,
                exception.getAttribute("key"),
                exception.getAttribute("path"),
                exception.getAttribute("handler")
            );
            exceptions.add(mapping);
            SymbolId exceptionConfig = configSymbol(projectKey, scope.moduleKey(), sourceRootKey, configXml, "struts-exception:" + scopeValue + ":" + i + ":" + type);
            SymbolId exceptionClass = SymbolId.classSymbol(projectKey, scope.moduleKey(), sourceRootKey, type);
            nodes.add(GraphNodeFactory.configNode(exceptionConfig));
            nodes.add(GraphNodeFactory.classNode(exceptionClass, NodeRole.CODE_TYPE));
            facts.add(fact(scope, exceptionConfig, RelationType.USES_CONFIG, exceptionClass, configXml, "exception-type", Confidence.CERTAIN));

            if (mapping.path() != null) {
                SymbolId target = jspOrActionTarget(projectKey, scope.moduleKey(), sourceRootKey, configXml, modulePrefix, mapping.path());
                addTargetNode(nodes, target);
                facts.add(fact(scope, exceptionConfig, RelationType.FORWARDS_TO, target, configXml, "exception-path:" + mapping.key(), Confidence.CERTAIN));
            }
            if (mapping.handler() != null) {
                SymbolId handlerClass = SymbolId.classSymbol(projectKey, scope.moduleKey(), sourceRootKey, mapping.handler());
                nodes.add(GraphNodeFactory.classNode(handlerClass, NodeRole.CODE_TYPE));
                facts.add(fact(scope, exceptionConfig, RelationType.USES_CONFIG, handlerClass, configXml, "exception-handler", Confidence.CERTAIN));
            }
        }
    }

    private void addPlugins(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path configXml,
        Document document,
        List<StrutsPlugin> plugins,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        NodeList pluginNodes = document.getElementsByTagName("plug-in");
        for (int i = 0; i < pluginNodes.getLength(); i++) {
            int pluginIndex = i;
            Element pluginElement = (Element) pluginNodes.item(i);
            String className = pluginElement.getAttribute("className");
            if (className == null || className.isBlank()) {
                className = pluginElement.getAttribute("class");
            }
            if (className == null || className.isBlank()) {
                continue;
            }
            Map<String, String> properties = pluginProperties(pluginElement);
            plugins.add(new StrutsPlugin(className, properties));

            SymbolId pluginConfig = SymbolId.logicalPath(
                SymbolKind.CONFIG_KEY,
                projectKey,
                scope.moduleKey(),
                sourceRootKey,
                configXml.toString(),
                "struts-plugin:" + pluginIndex
            );
            SymbolId pluginClass = SymbolId.classSymbol(projectKey, scope.moduleKey(), sourceRootKey, className);
            nodes.add(GraphNodeFactory.configNode(pluginConfig));
            nodes.add(GraphNodeFactory.classNode(pluginClass, NodeRole.CODE_TYPE));
            facts.add(fact(scope, pluginConfig, RelationType.USES_CONFIG, pluginClass, configXml, "struts-plugin", Confidence.CERTAIN));

            properties.forEach((name, value) -> {
                SymbolId property = SymbolId.logicalPath(
                    SymbolKind.CONFIG_KEY,
                    projectKey,
                    scope.moduleKey(),
                    sourceRootKey,
                    configXml.toString(),
                    "struts-plugin:" + pluginIndex + ":property:" + name
                );
                nodes.add(GraphNodeFactory.configNode(property));
                facts.add(fact(scope, pluginConfig, RelationType.USES_CONFIG, property, configXml, "plugin-property:" + name + "=" + value, Confidence.CERTAIN));
            });
        }
    }

    private Map<String, String> pluginProperties(Element pluginElement) {
        Map<String, String> properties = new LinkedHashMap<>();
        NodeList propertyNodes = pluginElement.getElementsByTagName("set-property");
        for (int i = 0; i < propertyNodes.getLength(); i++) {
            Element property = (Element) propertyNodes.item(i);
            String name = property.getAttribute("property");
            String value = property.getAttribute("value");
            if (name != null && !name.isBlank()) {
                properties.put(name.trim(), value == null ? "" : value.trim());
            }
        }
        return properties;
    }

    private void addControllers(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path configXml,
        Document document,
        List<StrutsControllerConfig> controllers,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        NodeList controllerNodes = document.getElementsByTagName("controller");
        for (int i = 0; i < controllerNodes.getLength(); i++) {
            int controllerIndex = i;
            Element controller = (Element) controllerNodes.item(i);
            Map<String, String> attributes = attributes(controller);
            controllers.add(new StrutsControllerConfig(attributes));

            SymbolId controllerConfig = SymbolId.logicalPath(
                SymbolKind.CONFIG_KEY,
                projectKey,
                scope.moduleKey(),
                sourceRootKey,
                configXml.toString(),
                "struts-controller:" + controllerIndex
            );
            nodes.add(GraphNodeFactory.configNode(controllerConfig));

            attributes.forEach((name, value) -> {
                SymbolId property = SymbolId.logicalPath(
                    SymbolKind.CONFIG_KEY,
                    projectKey,
                    scope.moduleKey(),
                    sourceRootKey,
                    configXml.toString(),
                    "struts-controller:" + controllerIndex + ":attribute:" + name
                );
                nodes.add(GraphNodeFactory.configNode(property));
                facts.add(fact(scope, controllerConfig, RelationType.USES_CONFIG, property, configXml, "controller-attribute:" + name + "=" + value, Confidence.CERTAIN));
            });

            addControllerClassFact(scope, projectKey, sourceRootKey, configXml, controllerConfig, "processorClass", attributes, nodes, facts);
            addControllerClassFact(scope, projectKey, sourceRootKey, configXml, controllerConfig, "multipartClass", attributes, nodes, facts);
        }
    }

    private void addActionMappingsConfig(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path configXml,
        Document document,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        NodeList actionMappingsNodes = document.getElementsByTagName("action-mappings");
        for (int i = 0; i < actionMappingsNodes.getLength(); i++) {
            Element actionMappings = (Element) actionMappingsNodes.item(i);
            String type = actionMappings.getAttribute("type");
            if (type == null || type.isBlank()) {
                continue;
            }
            SymbolId actionMappingsConfig = configSymbol(projectKey, scope.moduleKey(), sourceRootKey, configXml, "struts-action-mappings:" + i);
            SymbolId mappingClass = SymbolId.classSymbol(projectKey, scope.moduleKey(), sourceRootKey, type);
            nodes.add(GraphNodeFactory.configNode(actionMappingsConfig));
            nodes.add(GraphNodeFactory.classNode(mappingClass, NodeRole.CODE_TYPE));
            facts.add(fact(scope, actionMappingsConfig, RelationType.USES_CONFIG, mappingClass, configXml, "action-mappings-type", Confidence.CERTAIN));
        }
    }

    private void addControllerClassFact(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path configXml,
        SymbolId controllerConfig,
        String attributeName,
        Map<String, String> attributes,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        String className = attributes.get(attributeName);
        if (className == null || className.isBlank()) {
            return;
        }
        SymbolId classSymbol = SymbolId.classSymbol(projectKey, scope.moduleKey(), sourceRootKey, className);
        nodes.add(GraphNodeFactory.classNode(classSymbol, NodeRole.CODE_TYPE));
        facts.add(fact(scope, controllerConfig, RelationType.USES_CONFIG, classSymbol, configXml, "controller-" + attributeName, Confidence.CERTAIN));
    }

    private Map<String, String> attributes(Element element) {
        Map<String, String> result = new LinkedHashMap<>();
        var attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            var item = attributes.item(i);
            if (item.getNodeName() != null && item.getNodeValue() != null && !item.getNodeValue().isBlank()) {
                result.put(item.getNodeName(), item.getNodeValue().trim());
            }
        }
        return result;
    }

    private SymbolId configSymbol(String projectKey, String moduleKey, String sourceRootKey, Path configXml, String localId) {
        return SymbolId.logicalPath(SymbolKind.CONFIG_KEY, projectKey, moduleKey, sourceRootKey, configXml.toString(), localId);
    }

    public static SymbolId forwardConfigSymbol(String projectKey, String moduleKey, String sourceRootKey, String forwardName) {
        return SymbolId.logicalPath(SymbolKind.CONFIG_KEY, projectKey, moduleKey, sourceRootKey, "struts-forward", forwardName);
    }

    private SymbolId jspOrActionTarget(String projectKey, String moduleKey, String sourceRootKey, Path configXml, String modulePrefix, String path) {
        if (path != null && (path.endsWith(".do") || !path.contains("."))) {
            return actionPath(projectKey, moduleKey, sourceRootKey, moduleActionPath(modulePrefix, path));
        }
        if (looksLikeTilesDefinition(path)) {
            return configSymbol(projectKey, moduleKey, sourceRootKey, configXml, "tiles-definition:" + path);
        }
        return SymbolId.logicalPath(SymbolKind.JSP_PAGE, projectKey, moduleKey, sourceRootKey, path, null);
    }

    private boolean looksLikeTilesDefinition(String value) {
        return value != null && !value.isBlank() && !value.startsWith("/") && !value.contains("/") && value.contains(".");
    }

    private void addTargetNode(List<GraphNode> nodes, SymbolId target) {
        if (target.kind() == SymbolKind.ACTION_PATH) {
            nodes.add(GraphNodeFactory.actionPathNode(target));
        } else if (target.kind() == SymbolKind.JSP_PAGE) {
            nodes.add(GraphNodeFactory.jspNode(target, NodeRole.JSP_ARTIFACT));
        } else {
            nodes.add(GraphNodeFactory.configNode(target));
        }
    }

    public static SymbolId actionPath(String projectKey, String moduleKey, String sourceRootKey, String path) {
        return SymbolId.logicalPath(SymbolKind.ACTION_PATH, projectKey, moduleKey, sourceRootKey, normalizeActionPath(path), null);
    }

    public static String normalizeActionPath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("action path is required");
        }
        String normalized = path.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (normalized.endsWith(".do")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }
        return normalized;
    }

    public static String moduleActionPath(String modulePrefix, String actionPath) {
        String normalizedAction = normalizeActionPath(actionPath);
        if (modulePrefix == null || modulePrefix.isBlank() || modulePrefix.equals("/")) {
            return normalizedAction;
        }
        String normalizedPrefix = modulePrefix.startsWith("/") ? modulePrefix : "/" + modulePrefix;
        while (normalizedPrefix.endsWith("/") && normalizedPrefix.length() > 1) {
            normalizedPrefix = normalizedPrefix.substring(0, normalizedPrefix.length() - 1);
        }
        if (normalizedAction.startsWith(normalizedPrefix + "/")) {
            return normalizedAction;
        }
        return normalizedPrefix + normalizedAction;
    }

    private GraphFact fact(
        AnalyzerScope scope,
        SymbolId source,
        RelationType relationType,
        SymbolId target,
        Path configXml,
        String qualifier,
        Confidence confidence
    ) {
        return GraphFact.active(
            new FactKey(source, relationType, target, qualifier),
            new EvidenceKey(SourceType.STRUTS_CONFIG, "struts-config", configXml.toString(), 0, 0, qualifier),
            scope.projectId(),
            scope.snapshotId(),
            scope.analysisRunId(),
            scope.scopeKey(),
            confidence,
            SourceType.STRUTS_CONFIG
        );
    }
}
