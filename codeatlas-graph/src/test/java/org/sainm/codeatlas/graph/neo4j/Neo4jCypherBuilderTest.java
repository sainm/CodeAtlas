package org.sainm.codeatlas.graph.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.EvidenceKey;
import org.sainm.codeatlas.graph.model.FactKey;
import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNodeFactory;
import org.sainm.codeatlas.graph.model.NodeRole;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import java.util.List;
import org.junit.jupiter.api.Test;

class Neo4jCypherBuilderTest {
    @Test
    void buildsNodeUpsertCypherWithRoleLabelsAndProperties() {
        SymbolId method = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserService", "save", "()V");
        CypherStatement statement = new Neo4jCypherBuilder().upsertNode(GraphNodeFactory.methodNode(method, NodeRole.SERVICE));

        assertTrue(statement.cypher().contains("MERGE (n:Node {symbolId: $symbolId})"));
        assertTrue(statement.cypher().contains("SET n:CodeMember:Service"));
        assertEquals(method.value(), statement.parameters().get("symbolId"));
        @SuppressWarnings("unchecked")
        var properties = (java.util.Map<String, Object>) statement.parameters().get("properties");
        assertEquals("METHOD", properties.get("kind"));
        assertEquals(List.of("CODE_MEMBER", "SERVICE"), properties.get("roles"));
    }

    @Test
    void buildsFactUpsertCypherUsingFactAndEvidenceKeys() {
        SymbolId caller = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserAction", "execute", "()V");
        SymbolId callee = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserService", "save", "()V");
        GraphFact fact = GraphFact.active(
            new FactKey(caller, RelationType.CALLS, callee, "direct"),
            new EvidenceKey(SourceType.SPOON, "test", "UserAction.java", 10, 10, "call"),
            "shop",
            "snapshot-1",
            "run-1",
            "scope-java",
            Confidence.CERTAIN,
            SourceType.SPOON
        );

        CypherStatement statement = new Neo4jCypherBuilder().upsertFact(fact);

        assertTrue(statement.cypher().contains("MERGE (source)-[r:CALLS {factKey: $factKey, evidenceKey: $evidenceKey}]->(target)"));
        assertEquals(caller.value(), statement.parameters().get("sourceSymbolId"));
        assertEquals(callee.value(), statement.parameters().get("targetSymbolId"));
        @SuppressWarnings("unchecked")
        var properties = (java.util.Map<String, Object>) statement.parameters().get("properties");
        assertEquals("snapshot-1", properties.get("snapshotId"));
        assertEquals("CERTAIN", properties.get("confidence"));
    }
}
