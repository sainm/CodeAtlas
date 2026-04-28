package org.sainm.codeatlas.analyzers;

import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;
import java.util.List;

public record ProjectAnalysisResult(
    List<GraphNode> nodes,
    List<GraphFact> facts
) {
    public ProjectAnalysisResult {
        nodes = List.copyOf(nodes);
        facts = List.copyOf(facts);
    }
}
