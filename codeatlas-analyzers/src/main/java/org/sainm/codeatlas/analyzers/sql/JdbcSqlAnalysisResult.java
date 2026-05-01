package org.sainm.codeatlas.analyzers.sql;

import java.util.List;
import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;

public record JdbcSqlAnalysisResult(
    List<GraphNode> nodes,
    List<GraphFact> facts
) {
    public JdbcSqlAnalysisResult {
        nodes = List.copyOf(nodes);
        facts = List.copyOf(facts);
    }
}

