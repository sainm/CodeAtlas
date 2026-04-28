package org.sainm.codeatlas.analyzers.seasar;

import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;
import java.util.List;

public record SeasarDiconAnalysisResult(
    List<SeasarComponent> components,
    List<GraphNode> nodes,
    List<GraphFact> facts
) {
    public SeasarDiconAnalysisResult {
        components = List.copyOf(components);
        nodes = List.copyOf(nodes);
        facts = List.copyOf(facts);
    }
}
