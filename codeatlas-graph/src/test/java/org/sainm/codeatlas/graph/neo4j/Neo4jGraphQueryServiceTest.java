package org.sainm.codeatlas.graph.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.SourceType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Neo4jGraphQueryServiceTest {
    @Test
    void mapsCallerRowsToNeighbors() {
        FakeQueryExecutor executor = new FakeQueryExecutor(List.of(Map.of(
            "symbolId", "method://shop/_root/src/main/java/com.acme.UserAction#execute()V",
            "relationType", "CALLS",
            "confidence", "CERTAIN",
            "sourceType", "SPOON",
            "evidenceKey", "SPOON|test|UserAction.java|1|1|call"
        )));
        Neo4jGraphQueryService service = new Neo4jGraphQueryService(executor, new Neo4jGraphQueryBuilder());

        List<GraphNeighbor> callers = service.findCallers("shop", "snapshot-1", "method://target", 5);

        assertEquals(1, callers.size());
        assertEquals("CALLS", callers.getFirst().relationType());
        assertEquals(Confidence.CERTAIN, callers.getFirst().confidence());
        assertEquals(SourceType.SPOON, callers.getFirst().sourceType());
        assertEquals("method://target", executor.lastStatement.parameters().get("symbolId"));
    }

    private static final class FakeQueryExecutor implements CypherQueryExecutor {
        private final List<Map<String, Object>> rows;
        private CypherStatement lastStatement;

        private FakeQueryExecutor(List<Map<String, Object>> rows) {
            this.rows = rows;
        }

        @Override
        public void execute(CypherStatement statement) {
            lastStatement = statement;
        }

        @Override
        public List<Map<String, Object>> query(CypherStatement statement) {
            lastStatement = statement;
            return rows;
        }
    }
}
