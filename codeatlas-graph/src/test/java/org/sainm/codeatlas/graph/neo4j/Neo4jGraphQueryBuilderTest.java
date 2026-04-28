package org.sainm.codeatlas.graph.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class Neo4jGraphQueryBuilderTest {
    @Test
    void buildsCallerQueryWithLatestActiveEvidenceFilter() {
        CypherStatement statement = new Neo4jGraphQueryBuilder().findCallers("shop", "snapshot-2", "method://x", 20);

        assertTrue(statement.cypher().contains("MATCH (caller:Node)-[r]->(callee:Node {symbolId: $symbolId})"));
        assertTrue(statement.cypher().contains("collect(r)[0] AS latest"));
        assertTrue(statement.cypher().contains("latest.tombstone = false"));
        assertEquals("shop", statement.parameters().get("projectId"));
        assertEquals("snapshot-2", statement.parameters().get("snapshotId"));
        assertEquals("method://x", statement.parameters().get("symbolId"));
        assertEquals(List.of("CALLS"), statement.parameters().get("relationTypes"));
    }

    @Test
    void capsImpactPathDepth() {
        CypherStatement statement = new Neo4jGraphQueryBuilder().findImpactPaths("shop", "snapshot-2", "method://x", 99, 10);

        assertTrue(statement.cypher().contains("[*1..12]"));
        assertTrue(statement.cypher().contains("all(r IN relationships(path)"));
        assertTrue(((List<?>) statement.parameters().get("relationTypes")).contains("INJECTS"));
        assertTrue(((List<?>) statement.parameters().get("relationTypes")).contains("BRIDGES_TO"));
        assertTrue(((List<?>) statement.parameters().get("relationTypes")).contains("DECLARES"));
        assertTrue(((List<?>) statement.parameters().get("relationTypes")).contains("READS_PARAM"));
    }

    @Test
    void buildsVariableTraceSourceAndSinkQueries() {
        Neo4jGraphQueryBuilder builder = new Neo4jGraphQueryBuilder();
        CypherStatement sources = builder.traceVariableSources("shop", "snapshot-2", "request-parameter://id", 25);
        CypherStatement sinks = builder.traceVariableSinks("shop", "snapshot-2", "request-parameter://id", 25);

        assertTrue(sources.cypher().contains("[r:WRITES_PARAM]"));
        assertTrue(sinks.cypher().contains("[r:READS_PARAM]"));
        assertEquals(List.of("WRITES_PARAM"), sources.parameters().get("relationTypes"));
        assertEquals(List.of("READS_PARAM"), sinks.parameters().get("relationTypes"));
    }

    @Test
    void buildsJspBackendFlowQuery() {
        CypherStatement statement = new Neo4jGraphQueryBuilder().findJspBackendFlow("shop", "snapshot-2", "jsp-page://x", 5, 20);

        assertTrue(statement.cypher().contains("(jsp:Node {symbolId: $symbolId})-[*1..5]->(target:Node)"));
        assertTrue(((List<?>) statement.parameters().get("relationTypes")).contains("SUBMITS_TO"));
        assertTrue(((List<?>) statement.parameters().get("relationTypes")).contains("ROUTES_TO"));
        assertTrue(((List<?>) statement.parameters().get("relationTypes")).contains("BINDS_TO"));
    }
}
