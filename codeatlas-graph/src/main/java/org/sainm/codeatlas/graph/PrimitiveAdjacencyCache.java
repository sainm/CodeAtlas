package org.sainm.codeatlas.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sainm.codeatlas.facts.CurrentFactReport;
import org.sainm.codeatlas.facts.FactRecord;

public final class PrimitiveAdjacencyCache {
    private final Map<String, List<String>> calleesByCaller;
    private final Map<String, List<String>> callersByCallee;

    private PrimitiveAdjacencyCache(
            Map<String, List<String>> calleesByCaller,
            Map<String, List<String>> callersByCallee) {
        this.calleesByCaller = copy(calleesByCaller);
        this.callersByCallee = copy(callersByCallee);
    }

    public static PrimitiveAdjacencyCache from(CurrentFactReport report) {
        if (report == null) {
            throw new IllegalArgumentException("report is required");
        }
        Map<String, List<String>> calleesByCaller = new LinkedHashMap<>();
        Map<String, List<String>> callersByCallee = new LinkedHashMap<>();
        for (FactRecord fact : report.facts()) {
            if (!isPrimitiveCallEdge(fact)) {
                continue;
            }
            calleesByCaller.computeIfAbsent(fact.sourceIdentityId(), ignored -> new ArrayList<>())
                    .add(fact.targetIdentityId());
            callersByCallee.computeIfAbsent(fact.targetIdentityId(), ignored -> new ArrayList<>())
                    .add(fact.sourceIdentityId());
        }
        return new PrimitiveAdjacencyCache(calleesByCaller, callersByCallee);
    }

    public List<String> callees(String callerIdentityId) {
        return calleesByCaller.getOrDefault(callerIdentityId, List.of());
    }

    public List<String> callers(String calleeIdentityId) {
        return callersByCallee.getOrDefault(calleeIdentityId, List.of());
    }

    public int edgeCount() {
        return calleesByCaller.values().stream().mapToInt(List::size).sum();
    }

    private static boolean isPrimitiveCallEdge(FactRecord fact) {
        return fact.relationType().name().equals("CALLS") || fact.relationType().name().equals("INVOKES");
    }

    private static Map<String, List<String>> copy(Map<String, List<String>> values) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        values.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return Map.copyOf(result);
    }
}
