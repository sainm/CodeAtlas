package org.sainm.codeatlas.analyzers.java;

import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;
import java.util.List;

public record RequestParameterGraphResult(
    List<GraphNode> nodes,
    List<GraphFact> facts
) {
    public RequestParameterGraphResult {
        nodes = List.copyOf(nodes);
        facts = List.copyOf(facts);
    }
}
