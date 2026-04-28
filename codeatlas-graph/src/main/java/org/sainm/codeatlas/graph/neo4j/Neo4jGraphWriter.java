package org.sainm.codeatlas.graph.neo4j;

import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;
import java.util.Collection;

public final class Neo4jGraphWriter {
    private final CypherExecutor executor;
    private final Neo4jCypherBuilder cypherBuilder;

    public Neo4jGraphWriter(CypherExecutor executor, Neo4jCypherBuilder cypherBuilder) {
        this.executor = executor;
        this.cypherBuilder = cypherBuilder;
    }

    public void applySchema() {
        new Neo4jSchemaCypher().statements().forEach(executor::execute);
    }

    public void upsertNodes(Collection<GraphNode> nodes) {
        nodes.forEach(node -> executor.execute(cypherBuilder.upsertNode(node)));
    }

    public void upsertFacts(Collection<GraphFact> facts) {
        facts.forEach(fact -> executor.execute(cypherBuilder.upsertFact(fact)));
    }
}
