package org.sainm.codeatlas.analyzers.sql;

import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;
import java.util.List;

public record MyBatisMapperInterfaceAnalysisResult(
    List<MyBatisMapperMethod> methods,
    List<GraphNode> nodes,
    List<GraphFact> facts
) {
    public MyBatisMapperInterfaceAnalysisResult {
        methods = List.copyOf(methods);
        nodes = List.copyOf(nodes);
        facts = List.copyOf(facts);
    }
}
