package org.sainm.codeatlas.graph.neo4j;

import org.sainm.codeatlas.graph.model.RelationType;
import java.util.List;
import java.util.Map;

public final class Neo4jGraphQueryBuilder {
    public CypherStatement findCallers(String projectId, String snapshotId, String symbolId, int limit) {
        return new CypherStatement("""
            MATCH (caller:Node)-[r]->(callee:Node {symbolId: $symbolId})
            WHERE r.projectId = $projectId
              AND r.snapshotId <= $snapshotId
              AND type(r) IN $relationTypes
            WITH caller, callee, r
            ORDER BY r.snapshotId DESC
            WITH caller, callee, r.factKey AS factKey, r.evidenceKey AS evidenceKey, collect(r)[0] AS latest
            WHERE latest.active = true AND latest.tombstone = false
            RETURN caller.symbolId AS symbolId,
                   type(latest) AS relationType,
                   latest.confidence AS confidence,
                   latest.sourceType AS sourceType,
                   latest.evidenceKey AS evidenceKey
            ORDER BY symbolId
            LIMIT $limit
            """, baseParameters(projectId, snapshotId, symbolId, limit, List.of(RelationType.CALLS.name())));
    }

    public CypherStatement findCallees(String projectId, String snapshotId, String symbolId, int limit) {
        return new CypherStatement("""
            MATCH (caller:Node {symbolId: $symbolId})-[r]->(callee:Node)
            WHERE r.projectId = $projectId
              AND r.snapshotId <= $snapshotId
              AND type(r) IN $relationTypes
            WITH caller, callee, r
            ORDER BY r.snapshotId DESC
            WITH caller, callee, r.factKey AS factKey, r.evidenceKey AS evidenceKey, collect(r)[0] AS latest
            WHERE latest.active = true AND latest.tombstone = false
            RETURN callee.symbolId AS symbolId,
                   type(latest) AS relationType,
                   latest.confidence AS confidence,
                   latest.sourceType AS sourceType,
                   latest.evidenceKey AS evidenceKey
            ORDER BY symbolId
            LIMIT $limit
            """, baseParameters(projectId, snapshotId, symbolId, limit, List.of(RelationType.CALLS.name())));
    }

    public CypherStatement findImpactPaths(String projectId, String snapshotId, String changedSymbolId, int maxDepth, int limit) {
        int depth = Math.max(1, Math.min(maxDepth, 12));
        return new CypherStatement("""
            MATCH path = (entry:Node)-[*1..%d]->(changed:Node {symbolId: $symbolId})
            WHERE all(r IN relationships(path)
                WHERE r.projectId = $projectId
                  AND r.snapshotId <= $snapshotId
                  AND r.active = true
                  AND r.tombstone = false
                  AND type(r) IN $relationTypes)
            RETURN [n IN nodes(path) | n.symbolId] AS symbols,
                   [r IN relationships(path) | type(r)] AS relations,
                   [r IN relationships(path) | r.confidence] AS confidenceList,
                   [r IN relationships(path) | r.sourceType] AS sourceTypeList
            LIMIT $limit
            """.formatted(depth), baseParameters(projectId, snapshotId, changedSymbolId, limit, List.of(
            RelationType.CALLS.name(),
            RelationType.BRIDGES_TO.name(),
            RelationType.IMPLEMENTS.name(),
            RelationType.INJECTS.name(),
            RelationType.ROUTES_TO.name(),
            RelationType.SUBMITS_TO.name(),
            RelationType.BINDS_TO.name(),
            RelationType.FORWARDS_TO.name(),
            RelationType.READS_TABLE.name(),
            RelationType.WRITES_TABLE.name()
        )));
    }

    public CypherStatement traceVariableSources(String projectId, String snapshotId, String parameterSymbolId, int limit) {
        return new CypherStatement("""
            MATCH (source:Node)-[r:WRITES_PARAM]->(parameter:Node {symbolId: $symbolId})
            WHERE r.projectId = $projectId
              AND r.snapshotId <= $snapshotId
            WITH source, parameter, r
            ORDER BY r.snapshotId DESC
            WITH source, parameter, r.factKey AS factKey, r.evidenceKey AS evidenceKey, collect(r)[0] AS latest
            WHERE latest.active = true AND latest.tombstone = false
            RETURN source.symbolId AS symbolId,
                   type(latest) AS relationType,
                   latest.qualifier AS qualifier,
                   latest.confidence AS confidence,
                   latest.sourceType AS sourceType,
                   latest.evidenceKey AS evidenceKey
            ORDER BY symbolId
            LIMIT $limit
            """, baseParameters(projectId, snapshotId, parameterSymbolId, limit, List.of(RelationType.WRITES_PARAM.name())));
    }

    public CypherStatement traceVariableSinks(String projectId, String snapshotId, String parameterSymbolId, int limit) {
        return new CypherStatement("""
            MATCH (sink:Node)-[r:READS_PARAM]->(parameter:Node {symbolId: $symbolId})
            WHERE r.projectId = $projectId
              AND r.snapshotId <= $snapshotId
            WITH sink, parameter, r
            ORDER BY r.snapshotId DESC
            WITH sink, parameter, r.factKey AS factKey, r.evidenceKey AS evidenceKey, collect(r)[0] AS latest
            WHERE latest.active = true AND latest.tombstone = false
            RETURN sink.symbolId AS symbolId,
                   type(latest) AS relationType,
                   latest.qualifier AS qualifier,
                   latest.confidence AS confidence,
                   latest.sourceType AS sourceType,
                   latest.evidenceKey AS evidenceKey
            ORDER BY symbolId
            LIMIT $limit
            """, baseParameters(projectId, snapshotId, parameterSymbolId, limit, List.of(RelationType.READS_PARAM.name())));
    }

    public CypherStatement findJspBackendFlow(String projectId, String snapshotId, String jspSymbolId, int maxDepth, int limit) {
        int depth = Math.max(1, Math.min(maxDepth, 12));
        return new CypherStatement("""
            MATCH path = (jsp:Node {symbolId: $symbolId})-[*1..%d]->(target:Node)
            WHERE all(r IN relationships(path)
                WHERE r.projectId = $projectId
                  AND r.snapshotId <= $snapshotId
                  AND r.active = true
                  AND r.tombstone = false
                  AND type(r) IN $relationTypes)
            RETURN [n IN nodes(path) | n.symbolId] AS symbols,
                   [r IN relationships(path) | type(r)] AS relations,
                   [r IN relationships(path) | r.confidence] AS confidenceList,
                   [r IN relationships(path) | r.sourceType] AS sourceTypeList,
                   target.symbolId AS targetSymbolId
            LIMIT $limit
            """.formatted(depth), baseParameters(projectId, snapshotId, jspSymbolId, limit, List.of(
            RelationType.DECLARES.name(),
            RelationType.SUBMITS_TO.name(),
            RelationType.ROUTES_TO.name(),
            RelationType.CALLS.name(),
            RelationType.IMPLEMENTS.name(),
            RelationType.INJECTS.name(),
            RelationType.BRIDGES_TO.name(),
            RelationType.BINDS_TO.name(),
            RelationType.READS_TABLE.name(),
            RelationType.WRITES_TABLE.name()
        )));
    }

    private Map<String, Object> baseParameters(
        String projectId,
        String snapshotId,
        String symbolId,
        int limit,
        List<String> relationTypes
    ) {
        return Map.of(
            "projectId", require(projectId, "projectId"),
            "snapshotId", require(snapshotId, "snapshotId"),
            "symbolId", require(symbolId, "symbolId"),
            "limit", Math.max(1, limit),
            "relationTypes", relationTypes
        );
    }

    private String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
