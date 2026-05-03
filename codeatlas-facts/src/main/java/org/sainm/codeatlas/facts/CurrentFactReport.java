package org.sainm.codeatlas.facts;

import java.util.List;

public record CurrentFactReport(
        String projectId,
        List<FactRecord> facts,
        List<MaterializedEdge> edges) {
    public CurrentFactReport {
        requireNonBlank(projectId, "projectId");
        facts = copyRequired(facts, "facts");
        edges = copyRequired(edges, "edges");
    }

    public static CurrentFactReport from(String projectId, List<FactRecord> activeFacts) {
        List<FactRecord> reportFacts = copyRequired(activeFacts, "activeFacts").stream()
                .filter(fact -> fact.active() && !fact.tombstone())
                .toList();
        return new CurrentFactReport(
                projectId,
                reportFacts,
                reportFacts.stream().map(MaterializedEdge::from).toList());
    }

    private static <T> List<T> copyRequired(List<T> values, String name) {
        if (values == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return List.copyOf(values);
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
