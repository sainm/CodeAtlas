package org.sainm.codeatlas.analyzers.spring;

import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;
import java.util.List;

public record SpringBeanAnalysisResult(
    List<SpringBeanDependency> dependencies,
    List<GraphNode> nodes,
    List<GraphFact> facts
) {
    public SpringBeanAnalysisResult {
        dependencies = List.copyOf(dependencies);
        nodes = List.copyOf(nodes);
        facts = List.copyOf(facts);
    }
}
