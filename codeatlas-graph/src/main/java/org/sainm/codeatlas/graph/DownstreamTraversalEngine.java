package org.sainm.codeatlas.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.sainm.codeatlas.facts.CurrentFactReport;
import org.sainm.codeatlas.facts.FactRecord;

public final class DownstreamTraversalEngine {
    private DownstreamTraversalEngine() {
    }

    public static DownstreamTraversalEngine defaults() {
        return new DownstreamTraversalEngine();
    }

    public DownstreamTraversalResult findDownstream(
            CurrentFactReport report,
            String entrypointId,
            int maxDepth,
            int maxPaths) {
        if (report == null) {
            throw new IllegalArgumentException("report is required");
        }
        if (entrypointId == null || entrypointId.isBlank()) {
            throw new IllegalArgumentException("entrypointId is required");
        }
        if (maxDepth <= 0) {
            return new DownstreamTraversalResult(entrypointId, List.of(), false);
        }
        int limit = maxPaths <= 0 ? 100 : maxPaths;
        Map<String, List<String>> targetsBySource = targetsBySource(report);
        Queue<List<String>> queue = new ArrayDeque<>();
        queue.add(List.of(entrypointId));
        List<ImpactPath> paths = new ArrayList<>();
        while (!queue.isEmpty()) {
            List<String> path = queue.remove();
            if (path.size() - 1 >= maxDepth) {
                continue;
            }
            for (String target : targetsBySource.getOrDefault(path.getLast(), List.of())) {
                if (path.contains(target)) {
                    continue;
                }
                List<String> nextPath = append(path, target);
                paths.add(new ImpactPath(nextPath));
                if (paths.size() >= limit) {
                    return new DownstreamTraversalResult(entrypointId, paths, true);
                }
                queue.add(nextPath);
            }
        }
        return new DownstreamTraversalResult(entrypointId, paths, false);
    }

    private static Map<String, List<String>> targetsBySource(CurrentFactReport report) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (FactRecord fact : report.facts()) {
            if (!isDownstreamFact(fact)) {
                continue;
            }
            result.computeIfAbsent(fact.sourceIdentityId(), ignored -> new ArrayList<>())
                    .add(fact.targetIdentityId());
        }
        return result;
    }

    private static boolean isDownstreamFact(FactRecord fact) {
        return switch (fact.relationType().name()) {
            case "ROUTES_TO", "SUBMITS_TO", "CALLS_HTTP", "CALLS", "INVOKES", "BINDS_TO",
                    "READS_TABLE", "WRITES_TABLE", "READS_COLUMN", "WRITES_COLUMN" -> true;
            default -> false;
        };
    }

    private static List<String> append(List<String> path, String identityId) {
        List<String> result = new ArrayList<>(path);
        result.add(identityId);
        return List.copyOf(result);
    }
}
