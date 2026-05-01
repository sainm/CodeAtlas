package org.sainm.codeatlas.analyzers.seasar;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.analyzers.java.ApplicationLayerClassifier;
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
import java.util.List;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public final class SeasarDiconAnalyzer {
    private final SafeXmlDocumentLoader xmlLoader = new SafeXmlDocumentLoader();

    public SeasarDiconAnalysisResult analyze(AnalyzerScope scope, String projectKey, String sourceRootKey, Path diconXml) {
        Document document = xmlLoader.load(diconXml);
        List<SeasarComponent> components = new ArrayList<>();
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphFact> facts = new ArrayList<>();

        NodeList componentNodes = document.getElementsByTagName("component");
        for (int i = 0; i < componentNodes.getLength(); i++) {
            Element component = (Element) componentNodes.item(i);
            String className = firstNonBlank(component.getAttribute("class"), component.getAttribute("className"));
            if (className == null) {
                continue;
            }
            SeasarComponent seasarComponent = new SeasarComponent(componentName(component, className, i), className);
            components.add(seasarComponent);
            addComponent(scope, projectKey, sourceRootKey, diconXml, seasarComponent, nodes, facts);
            addPropertyBindings(scope, projectKey, sourceRootKey, diconXml, seasarComponent, component, nodes, facts);
            addAspectBindings(scope, projectKey, sourceRootKey, diconXml, seasarComponent, component, nodes, facts);
        }
        addIncludes(scope, projectKey, sourceRootKey, diconXml, document, nodes, facts);

        return new SeasarDiconAnalysisResult(components, nodes, facts);
    }

    private void addComponent(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path diconXml,
        SeasarComponent component,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        SymbolId configKey = SymbolId.logicalPath(SymbolKind.CONFIG_KEY, projectKey, scope.moduleKey(), sourceRootKey, diconXml.toString(), "seasar:" + component.name());
        SymbolId classSymbol = SymbolId.classSymbol(projectKey, scope.moduleKey(), sourceRootKey, component.className());
        nodes.add(GraphNode.of(configKey, NodeRole.WEB_ENTRYPOINT));
        nodes.add(GraphNodeFactory.classNode(classSymbol, roleFor(component.className())));
        facts.add(GraphFact.active(
            new FactKey(configKey, RelationType.BINDS_TO, classSymbol, "seasar-component"),
            new EvidenceKey(SourceType.XML, "seasar-dicon", diconXml.toString(), 0, 0, component.name()),
            scope.projectId(),
            scope.snapshotId(),
            scope.analysisRunId(),
            scope.scopeKey(),
            Confidence.POSSIBLE,
            SourceType.XML
        ));
    }

    private void addPropertyBindings(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path diconXml,
        SeasarComponent component,
        Element componentElement,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        SymbolId sourceConfig = componentConfig(projectKey, scope.moduleKey(), sourceRootKey, diconXml, component.name());
        for (Element property : directChildren(componentElement, "property")) {
            String propertyName = property.getAttribute("name");
            String reference = referenceText(property);
            if (reference == null) {
                continue;
            }
            SymbolId targetConfig = componentConfig(projectKey, scope.moduleKey(), sourceRootKey, diconXml, reference);
            nodes.add(GraphNodeFactory.configNode(targetConfig));
            facts.add(possibleFact(
                scope,
                sourceConfig,
                RelationType.INJECTS,
                targetConfig,
                diconXml,
                "seasar-property:" + firstNonBlank(propertyName, reference)
            ));
        }
    }

    private void addAspectBindings(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path diconXml,
        SeasarComponent component,
        Element componentElement,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        SymbolId sourceConfig = componentConfig(projectKey, scope.moduleKey(), sourceRootKey, diconXml, component.name());
        for (Element aspect : directChildren(componentElement, "aspect")) {
            String reference = referenceText(aspect);
            if (reference == null) {
                continue;
            }
            SymbolId targetConfig = componentConfig(projectKey, scope.moduleKey(), sourceRootKey, diconXml, reference);
            nodes.add(GraphNodeFactory.configNode(targetConfig));
            facts.add(possibleFact(
                scope,
                sourceConfig,
                RelationType.USES_CONFIG,
                targetConfig,
                diconXml,
                "seasar-aspect:" + reference
            ));
        }
    }

    private void addIncludes(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path diconXml,
        Document document,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        SymbolId diconConfig = SymbolId.logicalPath(SymbolKind.CONFIG_KEY, projectKey, scope.moduleKey(), sourceRootKey, diconXml.toString(), "seasar-dicon");
        nodes.add(GraphNodeFactory.configNode(diconConfig));
        NodeList includeNodes = document.getElementsByTagName("include");
        for (int i = 0; i < includeNodes.getLength(); i++) {
            Element include = (Element) includeNodes.item(i);
            String path = include.getAttribute("path");
            if (path == null || path.isBlank()) {
                continue;
            }
            SymbolId includeConfig = SymbolId.logicalPath(SymbolKind.CONFIG_KEY, projectKey, scope.moduleKey(), sourceRootKey, path.trim(), "seasar-include");
            nodes.add(GraphNodeFactory.configNode(includeConfig));
            facts.add(possibleFact(scope, diconConfig, RelationType.USES_CONFIG, includeConfig, diconXml, "seasar-include:" + path.trim()));
        }
    }

    private GraphFact possibleFact(
        AnalyzerScope scope,
        SymbolId source,
        RelationType relationType,
        SymbolId target,
        Path diconXml,
        String qualifier
    ) {
        return GraphFact.active(
            new FactKey(source, relationType, target, qualifier),
            new EvidenceKey(SourceType.XML, "seasar-dicon", diconXml.toString(), 0, 0, qualifier),
            scope.projectId(),
            scope.snapshotId(),
            scope.analysisRunId(),
            scope.scopeKey(),
            Confidence.POSSIBLE,
            SourceType.XML
        );
    }

    private SymbolId componentConfig(String projectKey, String moduleKey, String sourceRootKey, Path diconXml, String componentName) {
        return SymbolId.logicalPath(SymbolKind.CONFIG_KEY, projectKey, moduleKey, sourceRootKey, diconXml.toString(), "seasar:" + componentName);
    }

    private NodeRole roleFor(String className) {
        return ApplicationLayerClassifier.roleForQualifiedName(className);
    }

    private String componentName(Element component, String className, int index) {
        String name = component.getAttribute("name");
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        String shortClassName = className.substring(className.lastIndexOf('.') + 1);
        if (!shortClassName.isBlank()) {
            return Character.toLowerCase(shortClassName.charAt(0)) + shortClassName.substring(1);
        }
        return "component" + index;
    }

    private List<Element> directChildren(Element parent, String tagName) {
        List<Element> children = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element element && element.getTagName().equals(tagName)) {
                children.add(element);
            }
        }
        return children;
    }

    private String referenceText(Element element) {
        String expression = element.getTextContent();
        if (expression == null) {
            return null;
        }
        String value = expression.trim();
        if (value.isBlank() || value.contains("(") || value.contains(")") || value.startsWith("\"") || value.startsWith("'")) {
            return null;
        }
        return value;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }
}
