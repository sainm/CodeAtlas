package org.sainm.codeatlas.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.sainm.codeatlas.facts.CurrentFactReport;
import org.sainm.codeatlas.facts.FactRecord;

public final class CallerTraversalEngine {
    private CallerTraversalEngine() {
    }

    public static CallerTraversalEngine defaults() {
        return new CallerTraversalEngine();
    }

    public CallerTraversalResult findCallers(
            CurrentFactReport report,
            String changedMethodId,
            int maxDepth,
            int maxPaths) {
        if (report == null) {
            throw new IllegalArgumentException("report is required");
        }
        if (changedMethodId == null || changedMethodId.isBlank()) {
            throw new IllegalArgumentException("changedMethodId is required");
        }
        if (maxDepth <= 0) {
            return new CallerTraversalResult(changedMethodId, List.of(), false);
        }
        int limit = maxPaths <= 0 ? 100 : maxPaths;
        Map<String, List<String>> callersByCallee = callersByCallee(report);
        Queue<List<String>> queue = new ArrayDeque<>();
        queue.add(List.of(changedMethodId));
        List<ImpactPath> paths = new ArrayList<>();
        boolean truncated = false;
        while (!queue.isEmpty()) {
            List<String> path = queue.remove();
            if (path.size() - 1 >= maxDepth) {
                continue;
            }
            for (String caller : callersByCallee.getOrDefault(path.getLast(), List.of())) {
                if (path.contains(caller)) {
                    continue;
                }
                List<String> nextPath = append(path, caller);
                paths.add(new ImpactPath(nextPath));
                if (paths.size() >= limit) {
                    truncated = true;
                    return new CallerTraversalResult(changedMethodId, paths, truncated);
                }
                queue.add(nextPath);
            }
        }
        return new CallerTraversalResult(changedMethodId, paths, truncated);
    }

    private static Map<String, List<String>> callersByCallee(CurrentFactReport report) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (FactRecord fact : report.facts()) {
            if (!isCallFact(fact)) {
                continue;
            }
            result.computeIfAbsent(fact.targetIdentityId(), ignored -> new ArrayList<>())
                    .add(fact.sourceIdentityId());
        }
        return result;
    }

    private static boolean isCallFact(FactRecord fact) {
        return fact.relationType().name().equals("CALLS") || fact.relationType().name().equals("INVOKES");
    }

    private static List<String> append(List<String> path, String identityId) {
        List<String> result = new ArrayList<>(path);
        result.add(identityId);
        return List.copyOf(result);
    }
}
