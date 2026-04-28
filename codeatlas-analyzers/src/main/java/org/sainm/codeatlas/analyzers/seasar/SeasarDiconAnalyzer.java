package org.sainm.codeatlas.analyzers.seasar;

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
            SeasarComponent seasarComponent = new SeasarComponent(component.getAttribute("name"), className);
            components.add(seasarComponent);
            addComponent(scope, projectKey, sourceRootKey, diconXml, seasarComponent, nodes, facts);
        }

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

    private NodeRole roleFor(String className) {
        String lower = className.toLowerCase();
        if (lower.endsWith("dao") || lower.contains(".dao.")) {
            return NodeRole.DAO;
        }
        if (lower.endsWith("service") || lower.contains(".service.")) {
            return NodeRole.SERVICE;
        }
        return NodeRole.CODE_TYPE;
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
