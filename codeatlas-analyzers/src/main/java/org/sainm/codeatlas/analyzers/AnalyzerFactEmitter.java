package org.sainm.codeatlas.analyzers;

import org.sainm.codeatlas.graph.model.GraphFact;

@FunctionalInterface
public interface AnalyzerFactEmitter {
    void emit(GraphFact fact);
}

