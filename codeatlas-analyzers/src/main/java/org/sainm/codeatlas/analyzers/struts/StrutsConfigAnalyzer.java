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
        try {
            Document document = xmlLoader.load(configXml);
            List<StrutsFormBean> forms = formBeans(document);
            Map<String, StrutsFormBean> formsByName = new HashMap<>();
            forms.forEach(form -> formsByName.put(form.name(), form));

            List<StrutsActionMapping> actions = new ArrayList<>();
            List<StrutsForward> forwards = new ArrayList<>();
            List<StrutsPlugin> plugins = new ArrayList<>();
            List<StrutsControllerConfig> controllers = new ArrayList<>();
            List<GraphNode> nodes = new ArrayList<>();
            List<GraphFact> facts = new ArrayList<>();
            addControllers(scope, projectKey, sourceRootKey, configXml, document, controllers, nodes, facts);
            NodeList actionNodes = document.getElementsByTagName("action");
            for (int i = 0; i < actionNodes.getLength(); i++) {
                Element action = (Element) actionNodes.item(i);
                StrutsActionMapping mapping = new StrutsActionMapping(
                    action.getAttribute("path"),
                    action.getAttribute("type"),
                    action.getAttribute("name"),
                    action.getAttribute("scope"),
                    action.getAttribute("input"),
                    action.getAttribute("parameter")
                );
                actions.add(mapping);
                addActionFacts(scope, projectKey, sourceRootKey, configXml, mapping, formsByName, nodes, facts);
                addForwards(scope, projectKey, sourceRootKey, configXml, mapping.path(), action, forwards, nodes, facts);
            }
            addPlugins(scope, projectKey, sourceRootKey, configXml, document, plugins, nodes, facts);

            return new StrutsConfigAnalysisResult(forms, actions, forwards, plugins, controllers, nodes, facts);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Failed to analyze Struts config: " + configXml, exception);
        }
    }

    private List<StrutsFormBean> formBeans(Document document) {
        List<StrutsFormBean> forms = new ArrayList<>();
        NodeList formNodes = document.getElementsByTagName("form-bean");
        for (int i = 0; i < formNodes.getLength(); i++) {
            Element form = (Element) formNodes.item(i);
            forms.add(new StrutsFormBean(form.getAttribute("name"), form.getAttribute("type")));
        }
        return forms;
    }

    private void addActionFacts(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path configXml,
        StrutsActionMapping mapping,
        Map<String, StrutsFormBean> formsByName,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        SymbolId actionPath = actionPath(projectKey, scope.moduleKey(), sourceRootKey, mapping.path());
        SymbolId actionClass = SymbolId.classSymbol(projectKey, scope.moduleKey(), sourceRootKey, mapping.type());
        nodes.add(GraphNodeFactory.actionPathNode(actionPath));
        nodes.add(GraphNodeFactory.classNode(actionClass, NodeRole.STRUTS_ACTION));
        facts.add(fact(scope, actionPath, RelationType.ROUTES_TO, actionClass, configXml, "struts-action", Confidence.CERTAIN));

        if (mapping.name() != null && formsByName.containsKey(mapping.name())) {
            StrutsFormBean formBean = formsByName.get(mapping.name());
            SymbolId formClass = SymbolId.classSymbol(projectKey, scope.moduleKey(), sourceRootKey, formBean.type());
            nodes.add(GraphNodeFactory.classNode(formClass, NodeRole.CODE_TYPE));
            facts.add(fact(scope, actionPath, RelationType.BINDS_TO, formClass, configXml, "action-form:" + mapping.name(), Confidence.CERTAIN));
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
    }

    private void addForwards(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path configXml,
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
            SymbolId jsp = SymbolId.logicalPath(SymbolKind.JSP_PAGE, projectKey, scope.moduleKey(), sourceRootKey, strutsForward.path(), null);
            nodes.add(GraphNodeFactory.jspNode(jsp, NodeRole.JSP_ARTIFACT));
            facts.add(fact(scope, actionPath, RelationType.FORWARDS_TO, jsp, configXml, "forward:" + strutsForward.name(), Confidence.CERTAIN));
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
