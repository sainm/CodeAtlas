package org.sainm.codeatlas.facts;

import java.util.ArrayList;
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

    public InMemoryFactStore() {
    }

    public static InMemoryFactStore defaults() {
        return new InMemoryFactStore();
    }

    @Override
    public void insert(FactRecord fact) {
        Objects.requireNonNull(fact, "fact");
        lock.writeLock().lock();
        try {
            facts.add(fact);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void insertAll(List<FactRecord> batch) {
        Objects.requireNonNull(batch, "batch");
        lock.writeLock().lock();
        try {
            facts.addAll(batch);
        } finally {
            lock.writeLock().unlock();
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
                    .filter(fact -> fact.targetIdentityId().equals(targetIdentityId))
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void upsertAll(List<FactRecord> batch) {
        Objects.requireNonNull(batch, "batch");
        lock.writeLock().lock();
        try {
            for (FactRecord fact : batch) {
                facts.removeIf(existing -> existing.factKey().equals(fact.factKey())
                        && existing.projectId().equals(fact.projectId())
                        && existing.snapshotId().equals(fact.snapshotId())
                        && existing.analyzerId().equals(fact.analyzerId())
                        && existing.scopeKey().equals(fact.scopeKey()));
                facts.add(fact);
            }
        } finally {
            lock.writeLock().unlock();
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
