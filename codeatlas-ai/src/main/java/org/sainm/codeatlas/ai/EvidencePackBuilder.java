package org.sainm.codeatlas.ai;

import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.SourceType;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class EvidencePackBuilder {
    public EvidencePack build(List<GraphFact> facts, Set<SourceType> allowedSources, int limit) {
        Set<SourceType> sources = allowedSources == null ? Set.of() : allowedSources;
        List<GraphFact> selected = facts.stream()
            .filter(fact -> sources.isEmpty() || sources.contains(fact.sourceType()))
            .sorted(Comparator.comparing(fact -> fact.evidenceKey().value()))
            .limit(Math.max(1, limit))
            .toList();
        return new EvidencePack(selected);
    }
}
