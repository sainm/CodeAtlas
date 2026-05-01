package org.sainm.codeatlas.analyzers.struts;

import java.util.List;
import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;

public record StrutsPluginInitXmlAnalysisResult(
    List<GraphNode> nodes,
    List<GraphFact> facts
) {
    public StrutsPluginInitXmlAnalysisResult {
        nodes = List.copyOf(nodes);
        facts = List.copyOf(facts);
    }
}

