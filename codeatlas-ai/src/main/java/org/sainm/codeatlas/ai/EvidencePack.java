package org.sainm.codeatlas.ai;

import org.sainm.codeatlas.graph.model.GraphFact;
import java.util.List;

public record EvidencePack(List<GraphFact> facts) {
    public EvidencePack {
        facts = List.copyOf(facts);
    }

    public int evidenceCount() {
        return facts.size();
    }
}

