package org.sainm.codeatlas.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import org.sainm.codeatlas.facts.CurrentFactReport;

public final class CachedCallerTraversalEngine {
    private final InMemoryAdjacencyCacheStore cacheStore;

    public CachedCallerTraversalEngine(InMemoryAdjacencyCacheStore cacheStore) {
        if (cacheStore == null) {
            throw new IllegalArgumentException("cacheStore is required");
        }
        this.cacheStore = cacheStore;
    }

    public CallerTraversalResult findCallers(
            CurrentFactReport report,
            String snapshotId,
            String changedMethodId,
            int maxDepth,
            int maxPaths) {
        return cacheStore.primitiveCallCache(report.projectId(), snapshotId)
                .map(cache -> findCallers(cache, changedMethodId, maxDepth, maxPaths))
                .orElseGet(() -> CallerTraversalEngine.defaults().findCallers(
                        report, changedMethodId, maxDepth, maxPaths));
    }

    private static CallerTraversalResult findCallers(
            PrimitiveAdjacencyCache cache,
            String changedMethodId,
            int maxDepth,
            int maxPaths) {
        if (maxDepth <= 0) {
            return new CallerTraversalResult(changedMethodId, List.of(), false);
        }
        int limit = maxPaths <= 0 ? 100 : maxPaths;
        Queue<List<String>> queue = new ArrayDeque<>();
        queue.add(List.of(changedMethodId));
        List<ImpactPath> paths = new ArrayList<>();
        while (!queue.isEmpty()) {
            List<String> path = queue.remove();
            if (path.size() - 1 >= maxDepth) {
                continue;
            }
            for (String caller : cache.callers(path.getLast())) {
                if (path.contains(caller)) {
                    continue;
                }
                List<String> nextPath = append(path, caller);
                paths.add(new ImpactPath(nextPath));
                if (paths.size() >= limit) {
                    return new CallerTraversalResult(changedMethodId, paths, true);
                }
                queue.add(nextPath);
            }
        }
        return new CallerTraversalResult(changedMethodId, paths, false);
    }

    private static List<String> append(List<String> path, String identityId) {
        List<String> result = new ArrayList<>(path);
        result.add(identityId);
        return List.copyOf(result);
    }
}
