package org.sainm.codeatlas.graph;

import java.util.List;

import org.sainm.codeatlas.facts.FactRecord;

public record DbImpactQueryResult(
        String targetIdentityId,
        List<FactRecord> databaseFacts,
        List<FactRecord> upstreamBindingFacts) {
    public DbImpactQueryResult {
        requireNonBlank(targetIdentityId, "targetIdentityId");
        databaseFacts = List.copyOf(databaseFacts == null ? List.of() : databaseFacts);
        upstreamBindingFacts = List.copyOf(upstreamBindingFacts == null ? List.of() : upstreamBindingFacts);
    }

    public List<FactRecord> readFacts() {
        return databaseFacts.stream()
                .filter(fact -> fact.relationType().name().equals("READS_TABLE")
                        || fact.relationType().name().equals("READS_COLUMN"))
                .toList();
    }

    public List<FactRecord> writeFacts() {
        return databaseFacts.stream()
                .filter(fact -> fact.relationType().name().equals("WRITES_TABLE")
                        || fact.relationType().name().equals("WRITES_COLUMN"))
                .toList();
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
