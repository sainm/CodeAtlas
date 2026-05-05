package org.sainm.codeatlas.facts;

import java.util.List;
import java.util.Set;

/**
 * Abstraction over fact storage backends.
 *
 * <p>Default implementation is {@link InMemoryFactStore}.
 * A Neo4j-backed implementation can be plugged in when the dependency is available.
 */
public interface FactStore {

    void insert(FactRecord fact);

    void insertAll(List<FactRecord> facts);

    /**
     * Upserts facts by factKey within the given batch, grouped by projectId/snapshotId/analyzerId/scopeKey.
     * Existing facts with matching factKey are replaced; new facts are inserted.
     *
     * <p>Backends MUST override this method. The default throws to prevent silent data duplication.
     */
    default void upsertAll(List<FactRecord> facts) {
        throw new UnsupportedOperationException(
                "upsertAll must be overridden by the backend implementation");
    }

    /**
     * Atomically replaces all facts for a given scope owner. New facts are upserted;
     * old facts matching the owner tuple that are NOT re-emitted are tombstoned.
     *
     * <p>This is the primary write path for scope-level analysis results.
     * If the operation fails mid-way, no changes are visible (atomic).
     */
    default void replaceScopeFacts(ScopeOwner owner, List<FactRecord> newFacts) {
        throw new UnsupportedOperationException(
                "replaceScopeFacts must be overridden by the backend implementation");
    }

    List<FactRecord> activeFacts(String projectId, String snapshotId);

    List<FactRecord> factsByRelation(String projectId, String snapshotId, String relationName);

    List<FactRecord> factsBySource(String projectId, String snapshotId, String sourceIdentityId);

    List<FactRecord> factsByTarget(String projectId, String snapshotId, String targetIdentityId);

    int tombstoneExpired(String projectId, String snapshotId);

    Set<String> activeSnapshots(String projectId);

    /**
     * Total count of active (non-tombstone) facts across all snapshots for a project.
     * Used by {@link StoreSizing} to detect when to recommend a backend switch.
     */
    long activeFactCount(String projectId);

    /**
     * Returns a {@link CurrentFactReport} for the given project and snapshot.
     */
    default CurrentFactReport report(String projectId, String snapshotId) {
        return CurrentFactReport.from(projectId, activeFacts(projectId, snapshotId));
    }
}
