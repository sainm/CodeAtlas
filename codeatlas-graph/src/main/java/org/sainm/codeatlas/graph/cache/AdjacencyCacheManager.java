package org.sainm.codeatlas.graph.cache;

import org.sainm.codeatlas.graph.store.ActiveFact;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AdjacencyCacheManager {
    private final Map<AdjacencyCacheKey, PrimitiveAdjacencyCache> caches = new HashMap<>();

    public PrimitiveAdjacencyCache getOrBuild(AdjacencyCacheKey key, List<ActiveFact> activeFacts) {
        return caches.computeIfAbsent(key, ignored -> PrimitiveAdjacencyCache.build(key, activeFacts));
    }

    public void invalidate(String projectId, String snapshotId) {
        caches.keySet().removeIf(key -> key.projectId().equals(projectId) && key.snapshotId().equals(snapshotId));
    }

    public int cacheCount() {
        return caches.size();
    }
}
