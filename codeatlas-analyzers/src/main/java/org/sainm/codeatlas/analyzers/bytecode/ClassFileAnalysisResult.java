package org.sainm.codeatlas.analyzers.bytecode;

import java.util.List;
import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;

public record ClassFileAnalysisResult(
    List<GraphNode> nodes,
    List<GraphFact> facts
) {
    public ClassFileAnalysisResult {
        nodes = List.copyOf(nodes);
        facts = List.copyOf(facts);
    }
}

