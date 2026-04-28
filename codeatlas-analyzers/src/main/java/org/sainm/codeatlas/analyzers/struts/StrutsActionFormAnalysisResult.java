package org.sainm.codeatlas.analyzers.struts;

import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;
import java.util.List;

public record StrutsActionFormAnalysisResult(
    List<StrutsActionFormField> fields,
    List<GraphNode> nodes,
    List<GraphFact> facts
) {
    public StrutsActionFormAnalysisResult {
        fields = List.copyOf(fields);
        nodes = List.copyOf(nodes);
        facts = List.copyOf(facts);
    }
}
