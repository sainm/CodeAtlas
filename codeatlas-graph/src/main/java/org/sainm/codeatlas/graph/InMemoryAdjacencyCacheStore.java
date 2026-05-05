package org.sainm.codeatlas.graph;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.sainm.codeatlas.facts.CurrentFactReport;
import org.sainm.codeatlas.facts.FactRecord;
import org.sainm.codeatlas.facts.RelationFamily;

/**
 * In-memory store for {@link PrimitiveAdjacencyCache} instances,
 * keyed by {@link AdjacencyCacheKey} including optional
 * {@link ClasspathCacheScope} for JAR/module-level granularity.
 *
 * <p>When a JAR or module changes, only caches scoped to that classpath
 * entry are invalidated; project-wide caches are invalidated as a whole.
 */
public final class InMemoryAdjacencyCacheStore {
    private final Map<AdjacencyCacheKey, PrimitiveAdjacencyCache> primitiveCaches = new LinkedHashMap<>();

    public synchronized PrimitiveAdjacencyCache rebuildPrimitiveCallCache(
            CurrentFactReport report,
            String snapshotId,
            ClasspathCacheScope scope) {
        if (report == null) {
            throw new IllegalArgumentException("report is required");
        }
        if (snapshotId == null || snapshotId.isBlank()) {
            throw new IllegalArgumentException("snapshotId is required");
        }
        if (scope == null) {
            throw new IllegalArgumentException("scope is required");
        }
        List<FactRecord> scopedFacts = scope.isProjectWide()
                ? report.facts()
                : filterFactsByScope(report, scope);
        PrimitiveAdjacencyCache cache = PrimitiveAdjacencyCache.from(
                CurrentFactReport.from(report.projectId(), scopedFacts));
        primitiveCaches.put(
                new AdjacencyCacheKey(report.projectId(), snapshotId, RelationFamily.CALL, scope),
                cache);
        return cache;
    }

    public synchronized PrimitiveAdjacencyCache rebuildPrimitiveCallCache(
            CurrentFactReport report,
            String snapshotId) {
        return rebuildPrimitiveCallCache(report, snapshotId, ClasspathCacheScope.projectWide());
    }

    public synchronized Optional<PrimitiveAdjacencyCache> primitiveCallCache(
            String projectId, String snapshotId) {
        return primitiveCallCache(projectId, snapshotId, ClasspathCacheScope.projectWide());
    }

    public synchronized Optional<PrimitiveAdjacencyCache> primitiveCallCache(
            String projectId, String snapshotId, ClasspathCacheScope scope) {
        return Optional.ofNullable(primitiveCaches.get(
                new AdjacencyCacheKey(projectId, snapshotId, RelationFamily.CALL, scope)));
    }

    /**
     * Invalidates all caches for a project+snapshot, regardless of scope.
     */
    public synchronized void invalidateProjectSnapshot(String projectId, String snapshotId) {
        primitiveCaches.keySet().removeIf(key -> key.projectId().equals(projectId)
                && key.snapshotId().equals(snapshotId));
    }

    /**
     * Invalidates only caches matching a specific classpath scope (e.g.,
     * a single JAR or module). Project-wide caches are also removed
     * if they cover this scope.
     */
    public synchronized void invalidateClasspathScope(
            String projectId, String snapshotId, ClasspathCacheScope scope) {
        primitiveCaches.keySet().removeIf(key ->
                key.projectId().equals(projectId)
                        && key.snapshotId().equals(snapshotId)
                        && (key.classpathScope().covers(scope)
                            || scope.covers(key.classpathScope())));
    }

    public synchronized int cacheCount() {
        return primitiveCaches.size();
    }

    private static List<FactRecord> filterFactsByScope(
            CurrentFactReport report, ClasspathCacheScope scope) {
        String fragment = scope.scopeKey();
        if (fragment.isBlank()) {
            return report.facts();
        }
        String anchored = "/" + fragment + "/";
        String anchoredFragment = "/" + fragment + "#";
        return report.facts().stream()
                .filter(f -> {
                    String combined = f.sourceIdentityId() + "\n" + f.targetIdentityId();
                    return combined.contains(anchored) || combined.contains(anchoredFragment);
                })
                .toList();
    }
}
