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
import java.util.List;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public final class StrutsTilesAnalyzer {
    private final SafeXmlDocumentLoader xmlLoader = new SafeXmlDocumentLoader();

    public StrutsTilesAnalysisResult analyze(AnalyzerScope scope, String projectKey, String sourceRootKey, Path tilesXml) {
        Document document = xmlLoader.load(tilesXml);
        List<StrutsTilesDefinition> definitions = new ArrayList<>();
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphFact> facts = new ArrayList<>();

        NodeList definitionNodes = document.getElementsByTagName("definition");
        for (int i = 0; i < definitionNodes.getLength(); i++) {
            Element definitionElement = (Element) definitionNodes.item(i);
            String name = definitionElement.getAttribute("name");
            if (name == null || name.isBlank()) {
                continue;
            }
            StrutsTilesDefinition definition = new StrutsTilesDefinition(
                name,
                definitionElement.getAttribute("path"),
                definitionElement.getAttribute("extends"),
                puts(definitionElement)
            );
            definitions.add(definition);
            addDefinitionFacts(scope, projectKey, sourceRootKey, tilesXml, definition, nodes, facts);
        }

        return new StrutsTilesAnalysisResult(definitions, nodes, facts);
    }

    private List<StrutsTilesPut> puts(Element definitionElement) {
        List<StrutsTilesPut> puts = new ArrayList<>();
        NodeList putNodes = definitionElement.getElementsByTagName("put");
        for (int i = 0; i < putNodes.getLength(); i++) {
            Element put = (Element) putNodes.item(i);
            String name = put.getAttribute("name");
            if (name == null || name.isBlank()) {
                continue;
            }
            puts.add(new StrutsTilesPut(name, put.getAttribute("value"), put.getAttribute("type")));
        }
        return puts;
    }

    private void addDefinitionFacts(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path tilesXml,
        StrutsTilesDefinition definition,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        SymbolId definitionConfig = configSymbol(projectKey, scope.moduleKey(), sourceRootKey, tilesXml, "tiles-definition:" + definition.name());
        nodes.add(GraphNodeFactory.configNode(definitionConfig));

        if (definition.extendsName() != null) {
            SymbolId parent = configSymbol(projectKey, scope.moduleKey(), sourceRootKey, tilesXml, "tiles-definition:" + definition.extendsName());
            nodes.add(GraphNodeFactory.configNode(parent));
            facts.add(fact(scope, definitionConfig, RelationType.EXTENDS, parent, tilesXml, "tiles-extends", Confidence.CERTAIN));
        }
        if (definition.path() != null) {
            addPathFact(scope, projectKey, sourceRootKey, tilesXml, definitionConfig, "tiles-path", definition.path(), nodes, facts);
        }
        for (StrutsTilesPut put : definition.puts()) {
            SymbolId putConfig = configSymbol(projectKey, scope.moduleKey(), sourceRootKey, tilesXml, "tiles-definition:" + definition.name() + ":put:" + put.name());
            nodes.add(GraphNodeFactory.configNode(putConfig));
            facts.add(fact(scope, definitionConfig, RelationType.USES_CONFIG, putConfig, tilesXml, "tiles-put:" + put.name(), Confidence.CERTAIN));
            if (put.value() != null) {
                addPathFact(scope, projectKey, sourceRootKey, tilesXml, putConfig, "tiles-put-value:" + put.name(), put.value(), nodes, facts);
            }
        }
    }

    private void addPathFact(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path tilesXml,
        SymbolId source,
        String qualifier,
        String path,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        if (!looksLikeJsp(path)) {
            return;
        }
        SymbolId jsp = SymbolId.logicalPath(SymbolKind.JSP_PAGE, projectKey, scope.moduleKey(), sourceRootKey, path, null);
        nodes.add(GraphNodeFactory.jspNode(jsp, NodeRole.JSP_ARTIFACT));
        facts.add(fact(scope, source, RelationType.FORWARDS_TO, jsp, tilesXml, qualifier, Confidence.CERTAIN));
    }

    private boolean looksLikeJsp(String value) {
        return value != null && (value.endsWith(".jsp") || value.endsWith(".jspx"));
    }

    private SymbolId configSymbol(String projectKey, String moduleKey, String sourceRootKey, Path tilesXml, String localId) {
        return SymbolId.logicalPath(SymbolKind.CONFIG_KEY, projectKey, moduleKey, sourceRootKey, tilesXml.toString(), localId);
    }

    private GraphFact fact(
        AnalyzerScope scope,
        SymbolId source,
        RelationType relationType,
        SymbolId target,
        Path tilesXml,
        String qualifier,
        Confidence confidence
    ) {
        return GraphFact.active(
            new FactKey(source, relationType, target, qualifier),
            new EvidenceKey(SourceType.STRUTS_CONFIG, "struts-tiles", tilesXml.toString(), 0, 0, qualifier),
            scope.projectId(),
            scope.snapshotId(),
            scope.analysisRunId(),
            scope.scopeKey(),
            confidence,
            SourceType.STRUTS_CONFIG
        );
    }
}
