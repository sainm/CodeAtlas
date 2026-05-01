package org.sainm.codeatlas.analyzers.struts;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.analyzers.xml.SafeXmlDocumentLoader;
import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.EvidenceKey;
import org.sainm.codeatlas.graph.model.FactKey;
import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;
import org.sainm.codeatlas.graph.model.GraphNodeFactory;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.model.SymbolKind;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class StrutsPluginInitXmlAnalyzer {
    private final SafeXmlDocumentLoader xmlLoader = new SafeXmlDocumentLoader();

    public StrutsPluginInitXmlAnalysisResult analyze(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path strutsConfigXml,
        StrutsConfigAnalysisResult strutsResult
    ) {
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphFact> facts = new ArrayList<>();
        Path webRoot = webRoot(strutsConfigXml);
        for (int pluginIndex = 0; pluginIndex < strutsResult.plugins().size(); pluginIndex++) {
            StrutsPlugin plugin = strutsResult.plugins().get(pluginIndex);
            SymbolId pluginConfig = configSymbol(projectKey, scope.moduleKey(), sourceRootKey, strutsConfigXml, "struts-plugin:" + pluginIndex);
            for (Map.Entry<String, String> property : plugin.properties().entrySet()) {
                for (String location : xmlLocations(property.getValue())) {
                    Path xmlFile = resolve(webRoot, strutsConfigXml, location);
                    SymbolId xmlConfig = configSymbol(projectKey, scope.moduleKey(), sourceRootKey, xmlFile, "plugin-init-xml:" + property.getKey());
                    nodes.add(GraphNodeFactory.configNode(pluginConfig));
                    nodes.add(GraphNodeFactory.configNode(xmlConfig));
                    facts.add(fact(scope, pluginConfig, RelationType.USES_CONFIG, xmlConfig, strutsConfigXml,
                        "plugin-init-xml:" + property.getKey() + "=" + location, Confidence.LIKELY));
                    if (Files.exists(xmlFile)) {
                        addXmlEntries(scope, projectKey, sourceRootKey, xmlFile, xmlConfig, nodes, facts);
                    }
                }
            }
        }
        return new StrutsPluginInitXmlAnalysisResult(nodes, facts);
    }

    private void addXmlEntries(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path xmlFile,
        SymbolId xmlConfig,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        Document document = xmlLoader.load(xmlFile);
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }
        SymbolId rootConfig = configSymbol(projectKey, scope.moduleKey(), sourceRootKey, xmlFile, "xml-root:" + root.getTagName());
        nodes.add(GraphNodeFactory.configNode(rootConfig));
        facts.add(fact(scope, xmlConfig, RelationType.DECLARES, rootConfig, xmlFile, "xml-root:" + root.getTagName(), Confidence.LIKELY));

        NodeList children = root.getChildNodes();
        int index = 0;
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) children.item(i);
            String identity = entryIdentity(child, index++);
            SymbolId entryConfig = configSymbol(projectKey, scope.moduleKey(), sourceRootKey, xmlFile, "xml-entry:" + child.getTagName() + ":" + identity);
            nodes.add(GraphNodeFactory.configNode(entryConfig));
            facts.add(fact(scope, rootConfig, RelationType.DECLARES, entryConfig, xmlFile, "xml-entry:" + child.getTagName(), Confidence.LIKELY));
            tableName(child).ifPresent(tableName -> {
                SymbolId table = SymbolId.logicalPath(SymbolKind.DB_TABLE, projectKey, scope.moduleKey(), "_database", tableName, null);
                nodes.add(GraphNodeFactory.tableNode(table));
                facts.add(fact(scope, entryConfig, RelationType.WRITES_TABLE, table, xmlFile, "plugin-init-table:" + tableName, Confidence.POSSIBLE));
            });
        }
    }

    private java.util.Optional<String> tableName(Element element) {
        for (String attribute : List.of("table", "tableName", "targetTable", "dbTable")) {
            String value = element.getAttribute(attribute);
            if (value != null && !value.isBlank()) {
                return java.util.Optional.of(value.trim());
            }
        }
        return java.util.Optional.empty();
    }

    private String entryIdentity(Element element, int index) {
        for (String attribute : List.of("id", "key", "name", "code", "value")) {
            String value = element.getAttribute(attribute);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return String.valueOf(index);
    }

    private List<String> xmlLocations(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> locations = new ArrayList<>();
        for (String part : value.split(",")) {
            String location = part.trim();
            if (location.endsWith(".xml")) {
                locations.add(location);
            }
        }
        return locations;
    }

    private Path resolve(Path webRoot, Path configXml, String location) {
        String normalized = location.trim().replace('\\', '/');
        if (normalized.startsWith("/")) {
            return webRoot.resolve(normalized.substring(1)).toAbsolutePath().normalize();
        }
        return configXml.getParent().resolve(normalized).toAbsolutePath().normalize();
    }

    private Path webRoot(Path strutsConfigXml) {
        Path current = strutsConfigXml.toAbsolutePath().normalize().getParent();
        while (current != null) {
            if (current.getFileName() != null && current.getFileName().toString().equals("WEB-INF")) {
                return current.getParent();
            }
            current = current.getParent();
        }
        return strutsConfigXml.toAbsolutePath().normalize().getParent();
    }

    private SymbolId configSymbol(String projectKey, String moduleKey, String sourceRootKey, Path path, String localId) {
        return SymbolId.logicalPath(SymbolKind.CONFIG_KEY, projectKey, moduleKey, sourceRootKey, path.toString(), localId);
    }

    private GraphFact fact(
        AnalyzerScope scope,
        SymbolId source,
        RelationType relationType,
        SymbolId target,
        Path evidencePath,
        String qualifier,
        Confidence confidence
    ) {
        return GraphFact.active(
            new FactKey(source, relationType, target, qualifier),
            new EvidenceKey(SourceType.XML, "struts-plugin-init-xml", evidencePath.toString(), 0, 0, qualifier),
            scope.projectId(),
            scope.snapshotId(),
            scope.analysisRunId(),
            scope.scopeKey(),
            confidence,
            SourceType.XML
        );
    }
}

