package org.sainm.codeatlas.graph.neo4j;

import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.SourceType;
import java.util.List;
import java.util.Map;

public final class Neo4jGraphQueryService {
    private final CypherQueryExecutor executor;
    private final Neo4jGraphQueryBuilder queryBuilder;

    public Neo4jGraphQueryService(CypherQueryExecutor executor, Neo4jGraphQueryBuilder queryBuilder) {
        this.executor = executor;
        this.queryBuilder = queryBuilder;
    }

    public List<GraphNeighbor> findCallers(String projectId, String snapshotId, String symbolId, int limit) {
        return executor.query(queryBuilder.findCallers(projectId, snapshotId, symbolId, limit))
            .stream()
            .map(this::neighbor)
            .toList();
    }

    public List<GraphNeighbor> findCallees(String projectId, String snapshotId, String symbolId, int limit) {
        return executor.query(queryBuilder.findCallees(projectId, snapshotId, symbolId, limit))
            .stream()
            .map(this::neighbor)
            .toList();
    }

    public CypherStatement findImpactPathsStatement(String projectId, String snapshotId, String changedSymbolId, int maxDepth, int limit) {
        return queryBuilder.findImpactPaths(projectId, snapshotId, changedSymbolId, maxDepth, limit);
    }

    private GraphNeighbor neighbor(Map<String, Object> row) {
        return new GraphNeighbor(
            string(row.get("symbolId")),
            string(row.get("relationType")),
            confidence(row.get("confidence")),
            sourceType(row.get("sourceType")),
            string(row.get("evidenceKey"))
        );
    }

    private String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private Confidence confidence(Object value) {
        if (value == null) {
            return Confidence.UNKNOWN;
        }
        return Confidence.valueOf(value.toString());
    }

    private SourceType sourceType(Object value) {
        if (value == null) {
            return SourceType.STATIC_RULE;
        }
        return SourceType.valueOf(value.toString());
    }
}
