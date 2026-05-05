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
     */
    default void upsertAll(List<FactRecord> facts) {
        insertAll(facts);
    }

    List<FactRecord> activeFacts(String projectId, String snapshotId);

    List<FactRecord> factsByRelation(String projectId, String snapshotId, String relationName);

    List<FactRecord> factsBySource(String projectId, String snapshotId, String sourceIdentityId);

    List<FactRecord> factsByTarget(String projectId, String snapshotId, String targetIdentityId);

    int tombstoneExpired(String projectId, String snapshotId);

    Set<String> activeSnapshots(String projectId);

    /**
     * Returns a {@link CurrentFactReport} for the given project and snapshot.
     */
    default CurrentFactReport report(String projectId, String snapshotId) {
        return CurrentFactReport.from(projectId, activeFacts(projectId, snapshotId));
    }
}
