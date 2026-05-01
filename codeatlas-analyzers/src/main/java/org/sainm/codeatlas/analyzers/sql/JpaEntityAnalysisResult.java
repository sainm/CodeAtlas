package org.sainm.codeatlas.analyzers.sql;

import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;
import java.util.List;

public record JpaEntityAnalysisResult(List<GraphNode> nodes, List<GraphFact> facts) {
}
