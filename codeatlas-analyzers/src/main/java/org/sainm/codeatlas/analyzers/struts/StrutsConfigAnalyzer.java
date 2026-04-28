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
            List<GraphNode> nodes = new ArrayList<>();
            List<GraphFact> facts = new ArrayList<>();
            NodeList actionNodes = document.getElementsByTagName("action");
            for (int i = 0; i < actionNodes.getLength(); i++) {
                Element action = (Element) actionNodes.item(i);
                StrutsActionMapping mapping = new StrutsActionMapping(
                    action.getAttribute("path"),
                    action.getAttribute("type"),
                    action.getAttribute("name"),
                    action.getAttribute("scope"),
                    action.getAttribute("input")
                );
                actions.add(mapping);
                addActionFacts(scope, projectKey, sourceRootKey, configXml, mapping, formsByName, nodes, facts);
                addForwards(scope, projectKey, sourceRootKey, configXml, mapping.path(), action, forwards, nodes, facts);
            }

            return new StrutsConfigAnalysisResult(forms, actions, forwards, nodes, facts);
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
