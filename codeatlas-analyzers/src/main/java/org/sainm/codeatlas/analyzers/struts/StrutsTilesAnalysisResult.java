package org.sainm.codeatlas.analyzers.struts;

import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;
import java.util.List;

public record StrutsTilesAnalysisResult(
    List<StrutsTilesDefinition> definitions,
    List<GraphNode> nodes,
    List<GraphFact> facts
) {
    public StrutsTilesAnalysisResult {
        definitions = List.copyOf(definitions);
        nodes = List.copyOf(nodes);
        facts = List.copyOf(facts);
    }
}
