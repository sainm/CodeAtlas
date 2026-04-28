package org.sainm.codeatlas.analyzers.sql;

import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;
import java.util.List;

public record MyBatisXmlAnalysisResult(
    List<MyBatisStatement> statements,
    List<GraphNode> nodes,
    List<GraphFact> facts
) {
    public MyBatisXmlAnalysisResult {
        statements = List.copyOf(statements);
        nodes = List.copyOf(nodes);
        facts = List.copyOf(facts);
    }
}
