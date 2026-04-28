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

class Neo4jGraphWriterTest {
    @Test
    void appliesSchemaAndWritesNodesThenFacts() {
        RecordingCypherExecutor executor = new RecordingCypherExecutor();
        Neo4jGraphWriter writer = new Neo4jGraphWriter(executor, new Neo4jCypherBuilder());
        SymbolId source = SymbolId.method("shop", "_root", "src/main/java", "com.acme.A", "a", "()V");
        SymbolId target = SymbolId.method("shop", "_root", "src/main/java", "com.acme.B", "b", "()V");
        GraphFact fact = GraphFact.active(
            new FactKey(source, RelationType.CALLS, target, "direct"),
            new EvidenceKey(SourceType.SPOON, "test", "A.java", 1, 1, "call"),
            "shop",
            "s1",
            "r1",
            "scope",
            Confidence.LIKELY,
            SourceType.SPOON
        );

        writer.applySchema();
        writer.upsertNodes(List.of(
            GraphNodeFactory.methodNode(source, NodeRole.SERVICE),
            GraphNodeFactory.methodNode(target, NodeRole.DAO)
        ));
        writer.upsertFacts(List.of(fact));

        assertTrue(executor.statements().getFirst().cypher().contains("CREATE CONSTRAINT"));
        assertEquals(8, executor.statements().size());
        assertTrue(executor.statements().getLast().cypher().contains("CALLS"));
    }
}
