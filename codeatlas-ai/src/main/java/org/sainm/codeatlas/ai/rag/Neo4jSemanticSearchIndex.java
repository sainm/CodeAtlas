package org.sainm.codeatlas.ai.rag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.sainm.codeatlas.ai.summary.ArtifactSummary;
import org.sainm.codeatlas.graph.neo4j.CypherExecutor;
import org.sainm.codeatlas.graph.neo4j.CypherStatement;

public final class Neo4jSemanticSearchIndex {
    private static final String SUMMARY_LABEL = "RagSummary";
    private final EmbeddingProvider embeddingProvider;
    private final String indexName;
    private final int dimensions;

    public Neo4jSemanticSearchIndex(EmbeddingProvider embeddingProvider, String indexName, int dimensions) {
        if (embeddingProvider == null) {
            throw new IllegalArgumentException("embeddingProvider is required");
        }
        if (!isSafeIdentifier(indexName)) {
            throw new IllegalArgumentException("indexName must contain only letters, digits, and underscores");
        }
        if (dimensions <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        this.embeddingProvider = embeddingProvider;
        this.indexName = indexName;
        this.dimensions = dimensions;
    }

    public CypherStatement schemaStatement() {
        return new CypherStatement(
            "CREATE VECTOR INDEX " + indexName + " IF NOT EXISTS "
                + "FOR (s:" + SUMMARY_LABEL + ") ON (s.embedding) "
                + "OPTIONS {indexConfig: {`vector.dimensions`: " + dimensions + ", `vector.similarity_function`: 'cosine'}}",
            Map.of()
        );
    }

    public void install(CypherExecutor executor) {
        requiredExecutor(executor).execute(schemaStatement());
    }

    public void upsertAll(CypherExecutor executor, String projectId, String snapshotId, List<ArtifactSummary> summaries) {
        CypherExecutor normalizedExecutor = requiredExecutor(executor);
        for (CypherStatement statement : upsertStatements(projectId, snapshotId, summaries)) {
            normalizedExecutor.execute(statement);
        }
    }

    public List<CypherStatement> upsertStatements(String projectId, String snapshotId, List<ArtifactSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return List.of();
        }
        List<CypherStatement> statements = new ArrayList<>();
        for (ArtifactSummary summary : summaries) {
            if (summary != null) {
                statements.add(upsertStatement(projectId, snapshotId, summary));
            }
        }
        return List.copyOf(statements);
    }

    public CypherStatement upsertStatement(String projectId, String snapshotId, ArtifactSummary summary) {
        if (summary == null) {
            throw new IllegalArgumentException("summary is required");
        }
        String normalizedProjectId = required(projectId, "projectId");
        String normalizedSnapshotId = required(snapshotId, "snapshotId");
        String symbolId = summary.symbolId().value();
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("summaryKey", summaryKey(normalizedProjectId, normalizedSnapshotId, symbolId));
        parameters.put("projectId", normalizedProjectId);
        parameters.put("snapshotId", normalizedSnapshotId);
        parameters.put("symbolId", symbolId);
        parameters.put("kind", summary.kind().name());
        parameters.put("title", summary.title());
        parameters.put("text", summary.text());
        parameters.put("searchableText", searchableText(summary));
        parameters.put("evidenceKeys", summary.evidenceKeys());
        parameters.put("embedding", vectorValues(embeddingProvider.embed(searchableText(summary))));
        return new CypherStatement(
            "MERGE (s:" + SUMMARY_LABEL + " {summaryKey: $summaryKey}) "
                + "SET s.projectId = $projectId, "
                + "s.snapshotId = $snapshotId, "
                + "s.symbolId = $symbolId, "
                + "s.kind = $kind, "
                + "s.title = $title, "
                + "s.text = $text, "
                + "s.searchableText = $searchableText, "
                + "s.evidenceKeys = $evidenceKeys, "
                + "s.embedding = $embedding, "
                + "s.updatedAt = datetime()",
            parameters
        );
    }

    public CypherStatement searchStatement(String projectId, String snapshotId, String query, int limit) {
        String normalizedProjectId = required(projectId, "projectId");
        String normalizedSnapshotId = required(snapshotId, "snapshotId");
        String normalizedQuery = required(query, "query");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("indexName", indexName);
        parameters.put("limit", limit);
        parameters.put("embedding", vectorValues(embeddingProvider.embed(normalizedQuery)));
        parameters.put("projectId", normalizedProjectId);
        parameters.put("snapshotId", normalizedSnapshotId);
        return new CypherStatement(
            "CALL db.index.vector.queryNodes($indexName, $limit, $embedding) "
                + "YIELD node, score "
                + "WHERE node.projectId = $projectId AND node.snapshotId = $snapshotId "
                + "RETURN node.symbolId AS symbolId, "
                + "node.title AS title, "
                + "node.text AS text, "
                + "node.kind AS kind, "
                + "node.evidenceKeys AS evidenceKeys, "
                + "score "
                + "ORDER BY score DESC",
            parameters
        );
    }

    private String searchableText(ArtifactSummary summary) {
        return summary.title() + "\n" + summary.text() + "\n" + summary.symbolId().value();
    }

    private String summaryKey(String projectId, String snapshotId, String symbolId) {
        return projectId + "|" + snapshotId + "|" + symbolId;
    }

    private List<Double> vectorValues(EmbeddingVector vector) {
        double[] values = vector.values();
        if (values.length != dimensions) {
            throw new IllegalArgumentException(
                "embedding dimension mismatch: expected " + dimensions + " but got " + values.length
            );
        }
        List<Double> result = new ArrayList<>(values.length);
        for (double value : values) {
            result.add(value);
        }
        return List.copyOf(result);
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private boolean isSafeIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (!Character.isLetterOrDigit(ch) && ch != '_') {
                return false;
            }
        }
        return true;
    }

    private CypherExecutor requiredExecutor(CypherExecutor executor) {
        if (executor == null) {
            throw new IllegalArgumentException("executor is required");
        }
        return executor;
    }
}
