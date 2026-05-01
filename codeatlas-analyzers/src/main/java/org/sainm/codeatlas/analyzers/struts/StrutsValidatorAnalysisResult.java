package org.sainm.codeatlas.analyzers.struts;

import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;
import java.util.List;

public record StrutsValidatorAnalysisResult(
    List<StrutsValidatorForm> forms,
    List<GraphNode> nodes,
    List<GraphFact> facts
) {
    public StrutsValidatorAnalysisResult {
        forms = List.copyOf(forms);
        nodes = List.copyOf(nodes);
        facts = List.copyOf(facts);
    }
}
