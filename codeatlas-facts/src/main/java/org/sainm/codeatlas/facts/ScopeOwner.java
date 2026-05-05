package org.sainm.codeatlas.facts;

/**
 * Identifies the owning scope of a batch of facts for tombstone ownership.
 *
 * <p>When {@link FactStore#replaceScopeFacts} is called with this owner,
 * all old facts matching the owner tuple that are NOT re-emitted in the
 * new batch are tombstoned.
 */
public record ScopeOwner(
        String projectId,
        String snapshotId,
        String analyzerId,
        String scopeKey) {
    public ScopeOwner {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("projectId is required");
        }
        if (snapshotId == null || snapshotId.isBlank()) {
            throw new IllegalArgumentException("snapshotId is required");
        }
        if (analyzerId == null || analyzerId.isBlank()) {
            throw new IllegalArgumentException("analyzerId is required");
        }
        if (scopeKey == null || scopeKey.isBlank()) {
            throw new IllegalArgumentException("scopeKey is required");
        }
    }
}
