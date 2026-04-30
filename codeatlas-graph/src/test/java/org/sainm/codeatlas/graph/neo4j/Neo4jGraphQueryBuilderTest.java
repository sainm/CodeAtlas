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
        assertTrue(((List<?>) statement.parameters().get("relationTypes")).contains("PASSES_PARAM"));
        assertTrue(((List<?>) statement.parameters().get("relationTypes")).contains("INCLUDES"));
    }

    @Test
    void buildsVariableTraceSourceAndSinkQueries() {
        Neo4jGraphQueryBuilder builder = new Neo4jGraphQueryBuilder();
        CypherStatement sources = builder.traceVariableSources("shop", "snapshot-2", "request-parameter://id", 25);
        CypherStatement sinks = builder.traceVariableSinks("shop", "snapshot-2", "request-parameter://id", 25);

        assertTrue(sources.cypher().contains("[r:WRITES_PARAM]"));
        assertTrue(sinks.cypher().contains("[r]-(sink:Node)"));
        assertTrue(sinks.cypher().contains("READS_PARAM"));
        assertTrue(sinks.cypher().contains("BINDS_TO"));
        assertTrue(sinks.cypher().contains("COVERED_BY"));
        assertEquals(List.of("WRITES_PARAM"), sources.parameters().get("relationTypes"));
        assertEquals(List.of("READS_PARAM", "BINDS_TO", "COVERED_BY"), sinks.parameters().get("relationTypes"));
    }

    @Test
    void buildsJspBackendFlowQuery() {
        CypherStatement statement = new Neo4jGraphQueryBuilder().findJspBackendFlow("shop", "snapshot-2", "jsp-page://x", 5, 20);

        assertTrue(statement.cypher().contains("(jsp:Node {symbolId: $symbolId})-[*1..5]->(target:Node)"));
        List<?> relationTypes = (List<?>) statement.parameters().get("relationTypes");
        assertTrue(relationTypes.contains("SUBMITS_TO"));
        assertTrue(relationTypes.contains("INCLUDES"));
        assertTrue(relationTypes.contains("FORWARDS_TO"));
        assertTrue(relationTypes.contains("ROUTES_TO"));
        assertTrue(relationTypes.contains("BINDS_TO"));
        assertTrue(relationTypes.contains("PASSES_PARAM"));
        assertTrue(relationTypes.contains("READS_PARAM"));
        assertTrue(relationTypes.contains("WRITES_PARAM"));
        assertTrue(relationTypes.contains("COVERED_BY"));
        assertTrue(relationTypes.contains("USES_CONFIG"));
        assertTrue(relationTypes.contains("EXTENDS"));
    }
}
