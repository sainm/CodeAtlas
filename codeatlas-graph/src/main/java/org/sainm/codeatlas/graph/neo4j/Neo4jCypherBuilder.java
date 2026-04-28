package org.sainm.codeatlas.graph.neo4j;

import org.sainm.codeatlas.graph.model.FactKey;
import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;
import org.sainm.codeatlas.graph.model.NodeRole;
import org.sainm.codeatlas.graph.model.SymbolId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class Neo4jCypherBuilder {
    public CypherStatement upsertNode(GraphNode node) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("symbolId", node.symbolId().value());
        parameters.put("properties", nodeProperties(node));
        String roleLabels = node.roles().stream()
            .map(this::roleLabel)
            .sorted()
            .collect(Collectors.joining(":"));
        String labelSuffix = roleLabels.isBlank() ? "" : ":" + roleLabels;
        String cypher = """
            MERGE (n:Node {symbolId: $symbolId})
            SET n += $properties
            SET n%s
            RETURN n
            """.formatted(labelSuffix);
        return new CypherStatement(cypher, parameters);
    }

    public CypherStatement upsertFact(GraphFact fact) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        FactKey key = fact.factKey();
        parameters.put("sourceSymbolId", key.source().value());
        parameters.put("targetSymbolId", key.target().value());
        parameters.put("factKey", key.value());
        parameters.put("evidenceKey", fact.evidenceKey().value());
        parameters.put("properties", factProperties(fact));
        String relationshipType = key.relationType().name();
        String cypher = """
            MATCH (source:Node {symbolId: $sourceSymbolId})
            MATCH (target:Node {symbolId: $targetSymbolId})
            MERGE (source)-[r:%s {factKey: $factKey, evidenceKey: $evidenceKey}]->(target)
            SET r += $properties
            RETURN r
            """.formatted(relationshipType);
        return new CypherStatement(cypher, parameters);
    }

    private Map<String, Object> nodeProperties(GraphNode node) {
        SymbolId symbol = node.symbolId();
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("symbolId", symbol.value());
        properties.put("kind", symbol.kind().name());
        properties.put("projectKey", symbol.projectKey());
        properties.put("moduleKey", symbol.moduleKey());
        properties.put("sourceRootKey", symbol.sourceRootKey());
        properties.put("ownerQualifiedName", symbol.ownerQualifiedName());
        properties.put("memberName", symbol.memberName());
        properties.put("descriptor", symbol.descriptor());
        properties.put("localId", symbol.localId());
        properties.put("normalizedPath", symbol.normalizedPathLower());
        properties.put("roles", node.roles().stream().map(NodeRole::name).sorted().toList());
        properties.putAll(node.properties());
        return properties;
    }

    private Map<String, Object> factProperties(GraphFact fact) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("factKey", fact.factKey().value());
        properties.put("evidenceKey", fact.evidenceKey().value());
        properties.put("qualifier", fact.factKey().qualifier());
        properties.put("projectId", fact.projectId());
        properties.put("snapshotId", fact.snapshotId());
        properties.put("analysisRunId", fact.analysisRunId());
        properties.put("scopeKey", fact.scopeKey());
        properties.put("confidence", fact.confidence().name());
        properties.put("confidenceRank", fact.confidence().rank());
        properties.put("sourceType", fact.sourceType().name());
        properties.put("active", fact.active());
        properties.put("tombstone", fact.tombstone());
        properties.put("evidenceSourceType", fact.evidenceKey().sourceType().name());
        properties.put("evidenceAnalyzer", fact.evidenceKey().analyzer());
        properties.put("evidencePath", fact.evidenceKey().path());
        properties.put("evidenceLineStart", fact.evidenceKey().lineStart());
        properties.put("evidenceLineEnd", fact.evidenceKey().lineEnd());
        properties.put("evidenceLocalPath", fact.evidenceKey().localPath());
        return properties;
    }

    private String roleLabel(NodeRole role) {
        String[] parts = role.name().toLowerCase().split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }
}
