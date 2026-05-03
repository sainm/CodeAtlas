package org.sainm.codeatlas.analyzers.source;

import java.util.List;

import org.sainm.codeatlas.facts.Evidence;
import org.sainm.codeatlas.facts.FactRecord;

public record JavaSourceFactBatch(
        List<FactRecord> facts,
        List<Evidence> evidence) {
    public JavaSourceFactBatch {
        facts = List.copyOf(facts == null ? List.of() : facts);
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
    }
}
