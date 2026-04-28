package org.sainm.codeatlas.analyzers.java;

import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;
import java.util.List;

public record JavaAnalysisResult(
    List<GraphNode> nodes,
    List<GraphFact> facts
) {
    public JavaAnalysisResult {
        nodes = List.copyOf(nodes);
        facts = List.copyOf(facts);
    }
}
