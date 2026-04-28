package org.sainm.codeatlas.analyzers.jsp;

import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;
import java.util.List;

public record JspAnalysisResult(
    List<JspForm> forms,
    List<GraphNode> nodes,
    List<GraphFact> facts
) {
    public JspAnalysisResult {
        forms = List.copyOf(forms);
        nodes = List.copyOf(nodes);
        facts = List.copyOf(facts);
    }
}
