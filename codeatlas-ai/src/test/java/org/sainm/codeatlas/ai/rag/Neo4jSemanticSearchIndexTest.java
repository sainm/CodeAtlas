package org.sainm.codeatlas.ai.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sainm.codeatlas.ai.summary.ArtifactSummary;
import org.sainm.codeatlas.ai.summary.SummaryKind;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.neo4j.CypherStatement;
import org.sainm.codeatlas.graph.neo4j.RecordingCypherExecutor;

class Neo4jSemanticSearchIndexTest {
    @Test
    void buildsVectorIndexSchemaStatement() {
        Neo4jSemanticSearchIndex index = new Neo4jSemanticSearchIndex(new DeterministicHashEmbeddingProvider(8), "codeatlas_summary_vector", 8);

        CypherStatement statement = index.schemaStatement();

        assertTrue(statement.cypher().contains("CREATE VECTOR INDEX codeatlas_summary_vector IF NOT EXISTS"));
        assertTrue(statement.cypher().contains("FOR (s:RagSummary) ON (s.embedding)"));
        assertTrue(statement.cypher().contains("`vector.dimensions`: 8"));
        assertTrue(statement.cypher().contains("`vector.similarity_function`: 'cosine'"));
    }

    @Test
    void buildsSummaryUpsertWithEmbeddingAndEvidence() {
        Neo4jSemanticSearchIndex index = new Neo4jSemanticSearchIndex(new DeterministicHashEmbeddingProvider(8), "codeatlas_summary_vector", 8);
        ArtifactSummary summary = new ArtifactSummary(
            SummaryKind.METHOD,
            SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserService", "save", "()V"),
            "UserService#save",
            "save user profile",
            List.of("e1", "e2")
        );

        CypherStatement statement = index.upsertStatement("shop", "s1", summary);

        assertTrue(statement.cypher().contains("MERGE (s:RagSummary {summaryKey: $summaryKey})"));
        assertTrue(statement.cypher().contains("s.embedding = $embedding"));
        assertEquals("shop|s1|" + summary.symbolId().value(), statement.parameters().get("summaryKey"));
        assertEquals("shop", statement.parameters().get("projectId"));
        assertEquals("s1", statement.parameters().get("snapshotId"));
        assertEquals(summary.symbolId().value(), statement.parameters().get("symbolId"));
        @SuppressWarnings("unchecked")
        List<Double> embedding = (List<Double>) statement.parameters().get("embedding");
        assertEquals(8, embedding.size());
        @SuppressWarnings("unchecked")
        List<String> evidenceKeys = (List<String>) statement.parameters().get("evidenceKeys");
        assertEquals(List.of("e1", "e2"), evidenceKeys);
    }

    @Test
    void buildsVectorQueryStatementWithEmbeddedQuestion() {
        Neo4jSemanticSearchIndex index = new Neo4jSemanticSearchIndex(new DeterministicHashEmbeddingProvider(8), "codeatlas_summary_vector", 8);

        CypherStatement statement = index.searchStatement("shop", "s1", "user save", 5);

        assertTrue(statement.cypher().contains("CALL db.index.vector.queryNodes($indexName, $limit, $embedding)"));
        assertTrue(statement.cypher().contains("node.projectId = $projectId"));
        assertTrue(statement.cypher().contains("node.snapshotId = $snapshotId"));
        assertEquals("codeatlas_summary_vector", statement.parameters().get("indexName"));
        assertEquals(5, statement.parameters().get("limit"));
        @SuppressWarnings("unchecked")
        List<Double> embedding = (List<Double>) statement.parameters().get("embedding");
        assertEquals(8, embedding.size());
    }

    @Test
    void installsSchemaAndUpsertsSummariesThroughCypherExecutor() {
        Neo4jSemanticSearchIndex index = new Neo4jSemanticSearchIndex(new DeterministicHashEmbeddingProvider(8), "codeatlas_summary_vector", 8);
        RecordingCypherExecutor executor = new RecordingCypherExecutor();
        ArtifactSummary summary = new ArtifactSummary(
            SummaryKind.CLASS,
            SymbolId.classSymbol("shop", "_root", "src/main/java", "com.acme.UserService"),
            "UserService",
            "user account service",
            List.of("e1")
        );

        index.install(executor);
        index.upsertAll(executor, "shop", "s1", List.of(summary));

        assertEquals(2, executor.statements().size());
        assertTrue(executor.statements().getFirst().cypher().startsWith("CREATE VECTOR INDEX"));
        assertTrue(executor.statements().getLast().cypher().startsWith("MERGE (s:RagSummary"));
    }

    @Test
    void rejectsUnsafeIndexNames() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new Neo4jSemanticSearchIndex(new DeterministicHashEmbeddingProvider(8), "bad index", 8)
        );
    }
}
