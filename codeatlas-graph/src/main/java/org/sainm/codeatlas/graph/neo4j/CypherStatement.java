package org.sainm.codeatlas.graph.neo4j;

import java.util.Map;

public record CypherStatement(
    String cypher,
    Map<String, Object> parameters
) {
    public CypherStatement {
        if (cypher == null || cypher.isBlank()) {
            throw new IllegalArgumentException("cypher is required");
        }
        cypher = cypher.trim();
        parameters = Map.copyOf(parameters);
    }
}
