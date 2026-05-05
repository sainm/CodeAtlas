package org.sainm.codeatlas.graph;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.sainm.codeatlas.facts.CurrentFactReport;
import org.sainm.codeatlas.facts.RelationFamily;

public final class InMemoryAdjacencyCacheStore {
    private final Map<AdjacencyCacheKey, PrimitiveAdjacencyCache> primitiveCaches = new LinkedHashMap<>();

    public synchronized PrimitiveAdjacencyCache rebuildPrimitiveCallCache(
            CurrentFactReport report,
            String snapshotId) {
        if (report == null) {
            throw new IllegalArgumentException("report is required");
        }
        if (snapshotId == null || snapshotId.isBlank()) {
            throw new IllegalArgumentException("snapshotId is required");
        }
        PrimitiveAdjacencyCache cache = PrimitiveAdjacencyCache.from(report);
        primitiveCaches.put(new AdjacencyCacheKey(report.projectId(), snapshotId, RelationFamily.CALL), cache);
        return cache;
    }

    public synchronized Optional<PrimitiveAdjacencyCache> primitiveCallCache(String projectId, String snapshotId) {
        return Optional.ofNullable(primitiveCaches.get(
                new AdjacencyCacheKey(projectId, snapshotId, RelationFamily.CALL)));
    }

    public synchronized void invalidateProjectSnapshot(String projectId, String snapshotId) {
        primitiveCaches.keySet().removeIf(key -> key.projectId().equals(projectId)
                && key.snapshotId().equals(snapshotId));
    }

    public synchronized int cacheCount() {
        return primitiveCaches.size();
    }
}
