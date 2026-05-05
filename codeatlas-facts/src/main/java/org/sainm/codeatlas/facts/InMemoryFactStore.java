package org.sainm.codeatlas.facts;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * In-memory implementation of {@link FactStore} with single-writer coordination
 * via a {@link ReadWriteLock}.
 *
 * <p>This is the default fact store used when no external database is configured.
 */
public final class InMemoryFactStore implements FactStore {
    private final List<FactRecord> facts = new ArrayList<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final List<CacheRebuildListener> rebuildListeners = new ArrayList<>();

    public InMemoryFactStore() {
    }

    public static InMemoryFactStore defaults() {
        return new InMemoryFactStore();
    }

    public void addCacheRebuildListener(CacheRebuildListener listener) {
        rebuildListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    @Override
    public void insert(FactRecord fact) {
        Objects.requireNonNull(fact, "fact");
        lock.writeLock().lock();
        try {
            StoreSizing.guardInsert(this, fact.projectId(), 1);
            facts.add(fact);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void insertAll(List<FactRecord> batch) {
        Objects.requireNonNull(batch, "batch");
        if (batch.isEmpty()) {
            return;
        }
        lock.writeLock().lock();
        try {
            StoreSizing.guardInsert(this, batch.getFirst().projectId(), batch.size());
            facts.addAll(batch);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void upsertAll(List<FactRecord> batch) {
        Objects.requireNonNull(batch, "batch");
        if (batch.isEmpty()) {
            return;
        }
        // Stable ordering to reduce deadlock risk
        List<FactRecord> ordered = BatchOperationSupport.defaults().stableOrder(batch);
        lock.writeLock().lock();
        try {
            StoreSizing.guardInsert(this, ordered.getFirst().projectId(), ordered.size());
            // Validate entire batch first
            for (FactRecord fact : ordered) {
                Objects.requireNonNull(fact, "batch element");
            }
            // Atomic: all removals then all additions
            for (FactRecord fact : ordered) {
                facts.removeIf(existing -> existing.factKey().equals(fact.factKey())
                        && existing.projectId().equals(fact.projectId())
                        && existing.snapshotId().equals(fact.snapshotId())
                        && existing.analyzerId().equals(fact.analyzerId())
                        && existing.scopeKey().equals(fact.scopeKey()));
            }
            facts.addAll(ordered);
            fireCacheRebuild(ordered);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void replaceScopeFacts(ScopeOwner owner, List<FactRecord> newFacts) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(newFacts, "newFacts");
        lock.writeLock().lock();
        try {
            StoreSizing.guardInsert(this, owner.projectId(), newFacts.size());
            // Validate entire batch first
            for (FactRecord fact : newFacts) {
                Objects.requireNonNull(fact, "batch element");
            }
            // Tombstone old facts from this scope that are not re-emitted
            Set<String> newFactKeys = new HashSet<>();
            for (FactRecord fact : newFacts) {
                newFactKeys.add(fact.factKey());
            }
            List<FactRecord> replacement = new ArrayList<>();
            for (FactRecord existing : facts) {
                if (existing.projectId().equals(owner.projectId())
                        && existing.snapshotId().equals(owner.snapshotId())
                        && existing.analyzerId().equals(owner.analyzerId())
                        && existing.scopeKey().equals(owner.scopeKey())
                        && !newFactKeys.contains(existing.factKey())) {
                    // Not re-emitted → mark tombstone
                    replacement.add(new FactRecord(
                            List.of(),
                            existing.factKey(),
                            existing.sourceIdentityId(),
                            existing.targetIdentityId(),
                            existing.relationType(),
                            existing.qualifier(),
                            existing.projectId(),
                            existing.snapshotId(),
                            existing.analysisRunId(),
                            existing.scopeRunId(),
                            existing.analyzerId(),
                            existing.scopeKey(),
                            existing.relationFamily(),
                            existing.schemaVersion(),
                            existing.active(),
                            existing.validFromSnapshot(),
                            existing.validToSnapshot(),
                            true,
                            existing.evidenceKey(),
                            existing.confidence(),
                            existing.priority(),
                            existing.sourceType()));
                } else {
                    replacement.add(existing);
                }
            }
            facts.clear();
            facts.addAll(replacement);
            // Upsert new facts
            for (FactRecord fact : newFacts) {
                facts.removeIf(existing -> existing.factKey().equals(fact.factKey())
                        && existing.projectId().equals(fact.projectId())
                        && existing.snapshotId().equals(fact.snapshotId())
                        && existing.analyzerId().equals(fact.analyzerId())
                        && existing.scopeKey().equals(fact.scopeKey()));
            }
            facts.addAll(newFacts);
            fireCacheRebuild(newFacts);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void fireCacheRebuild(List<FactRecord> writtenFacts) {
        if (rebuildListeners.isEmpty() || writtenFacts.isEmpty()) {
            return;
        }
        FactRecord sample = writtenFacts.getFirst();
        List<RelationFamily> families = writtenFacts.stream()
                .map(FactRecord::relationFamily)
                .distinct()
                .toList();
        int activeCount = (int) activeFactCount(sample.projectId());
        CacheRebuildRequest request = new CacheRebuildRequest(
                sample.projectId(), sample.snapshotId(), families, activeCount);
        for (CacheRebuildListener listener : rebuildListeners) {
            listener.requestRebuild(request);
        }
    }

    @Override
    public List<FactRecord> activeFacts(String projectId, String snapshotId) {
        lock.readLock().lock();
        try {
            return facts.stream()
                    .filter(fact -> fact.projectId().equals(projectId))
                    .filter(fact -> fact.snapshotId().equals(snapshotId))
                    .filter(fact -> fact.active() && !fact.tombstone())
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<FactRecord> factsByRelation(String projectId, String snapshotId, String relationName) {
        lock.readLock().lock();
        try {
            return facts.stream()
                    .filter(fact -> fact.projectId().equals(projectId))
                    .filter(fact -> fact.snapshotId().equals(snapshotId))
                    .filter(fact -> fact.active() && !fact.tombstone())
                    .filter(fact -> fact.relationType().name().equals(relationName))
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<FactRecord> factsBySource(String projectId, String snapshotId, String sourceIdentityId) {
        lock.readLock().lock();
        try {
            return facts.stream()
                    .filter(fact -> fact.projectId().equals(projectId))
                    .filter(fact -> fact.snapshotId().equals(snapshotId))
                    .filter(fact -> fact.active() && !fact.tombstone())
                    .filter(fact -> fact.sourceIdentityId().equals(sourceIdentityId))
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<FactRecord> factsByTarget(String projectId, String snapshotId, String targetIdentityId) {
        lock.readLock().lock();
        try {
            return facts.stream()
                    .filter(fact -> fact.projectId().equals(projectId))
                    .filter(fact -> fact.snapshotId().equals(snapshotId))
                    .filter(fact -> fact.active() && !fact.tombstone())
                    .filter(fact -> fact.targetIdentityId().equals(targetIdentityId))
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public long activeFactCount(String projectId) {
        lock.readLock().lock();
        try {
            return facts.stream()
                    .filter(fact -> fact.projectId().equals(projectId))
                    .filter(fact -> fact.active() && !fact.tombstone())
                    .count();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int tombstoneExpired(String projectId, String snapshotId) {
        lock.writeLock().lock();
        try {
            int before = facts.size();
            facts.removeIf(fact -> fact.projectId().equals(projectId)
                    && fact.snapshotId().equals(snapshotId)
                    && fact.tombstone());
            return before - facts.size();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Set<String> activeSnapshots(String projectId) {
        lock.readLock().lock();
        try {
            Set<String> snapshots = new LinkedHashSet<>();
            for (FactRecord fact : facts) {
                if (fact.projectId().equals(projectId) && fact.active() && !fact.tombstone()) {
                    snapshots.add(fact.snapshotId());
                }
            }
            return snapshots;
        } finally {
            lock.readLock().unlock();
        }
    }
}
