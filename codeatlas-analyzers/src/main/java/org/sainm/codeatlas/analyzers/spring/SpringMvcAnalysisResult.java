package org.sainm.codeatlas.analyzers.spring;

import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;
import java.util.List;

public record SpringMvcAnalysisResult(
    List<SpringEndpoint> endpoints,
    List<GraphNode> nodes,
    List<GraphFact> facts
) {
    public SpringMvcAnalysisResult {
        endpoints = List.copyOf(endpoints);
        nodes = List.copyOf(nodes);
        facts = List.copyOf(facts);
    }
}
