package org.sainm.codeatlas.facts;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sainm.codeatlas.symbols.IdentityType;

public final class InMemoryFactStagingStore {
    private final Map<String, StagedFactBatch> stagedBatches = new LinkedHashMap<>();
    private final Map<String, List<FactRecord>> activeFactsByProject = new LinkedHashMap<>();
    private final CacheRebuildListener cacheRebuildListener;

    public InMemoryFactStagingStore() {
        this(request -> {
        });
    }

    public InMemoryFactStagingStore(CacheRebuildListener cacheRebuildListener) {
        if (cacheRebuildListener == null) {
            throw new IllegalArgumentException("cacheRebuildListener is required");
        }
        this.cacheRebuildListener = cacheRebuildListener;
    }

    public synchronized StagedFactBatch stage(
            AnalysisRun analysisRun,
            List<ScopeRun> scopeRuns,
            List<FactRecord> facts,
            List<Evidence> evidence) {
        requireRunningAnalysisRun(analysisRun);
        List<ScopeRun> runningScopes = copyRequired(scopeRuns, "scopeRuns");
        List<FactRecord> stagedFacts = copyRequired(facts, "facts");
        List<Evidence> stagedEvidence = copyRequired(evidence, "evidence");
        ScopeIndex scopeIndex = ScopeIndex.from(analysisRun, runningScopes);
        Map<String, Evidence> evidenceByKey = validateEvidence(stagedEvidence, scopeIndex);
        validateFacts(stagedFacts, analysisRun, scopeIndex, evidenceByKey);

        StagedFactBatch batch = new StagedFactBatch(
                analysisRun.stage(),
                runningScopes.stream().map(ScopeRun::stage).toList(),
                stagedFacts,
                stagedEvidence);
        stagedBatches.put(analysisRun.analysisRunId(), batch);
        return batch;
    }

    public synchronized List<FactRecord> stagedFacts(String analysisRunId) {
        requireNonBlank(analysisRunId, "analysisRunId");
        StagedFactBatch batch = stagedBatches.get(analysisRunId);
        if (batch == null) {
            return List.of();
        }
        return batch.facts();
    }

    public synchronized List<Evidence> stagedEvidence(String analysisRunId) {
        requireNonBlank(analysisRunId, "analysisRunId");
        StagedFactBatch batch = stagedBatches.get(analysisRunId);
        if (batch == null) {
            return List.of();
        }
        return batch.evidence();
    }

    public synchronized CommittedFactBatch commit(String analysisRunId) {
        requireNonBlank(analysisRunId, "analysisRunId");
        StagedFactBatch staged = requireStagedBatch(analysisRunId);
        CommittedFactBatch committed = new CommittedFactBatch(
                staged.analysisRun().commit(),
                staged.scopeRuns().stream().map(ScopeRun::commit).toList(),
                staged.facts(),
                staged.evidence());
        List<FactRecord> activeFacts = mergeActiveFacts(
                activeFacts(committed.analysisRun().projectId()),
                committed.scopeRuns(),
                committed.facts());
        activeFactsByProject.put(committed.analysisRun().projectId(), activeFacts);
        stagedBatches.remove(analysisRunId);
        requestCacheRebuild(committed, activeFacts.size());
        return committed;
    }

    public synchronized AnalysisRun rollback(String analysisRunId) {
        requireNonBlank(analysisRunId, "analysisRunId");
        StagedFactBatch staged = requireStagedBatch(analysisRunId);
        stagedBatches.remove(analysisRunId);
        return staged.analysisRun().rollback();
    }

    public synchronized AnalysisRun fail(String analysisRunId) {
        requireNonBlank(analysisRunId, "analysisRunId");
        StagedFactBatch staged = requireStagedBatch(analysisRunId);
        stagedBatches.remove(analysisRunId);
        return staged.analysisRun().fail();
    }

    public synchronized List<FactRecord> activeFacts(String projectId) {
        requireNonBlank(projectId, "projectId");
        return activeFactsByProject.getOrDefault(projectId, List.of());
    }

    public synchronized CurrentFactReport currentReport(String projectId) {
        requireNonBlank(projectId, "projectId");
        return CurrentFactReport.from(projectId, activeFacts(projectId));
    }

    private StagedFactBatch requireStagedBatch(String analysisRunId) {
        StagedFactBatch staged = stagedBatches.get(analysisRunId);
        if (staged == null) {
            throw new IllegalArgumentException("analysisRun has no staged batch: " + analysisRunId);
        }
        return staged;
    }

    private static List<FactRecord> mergeActiveFacts(
            List<FactRecord> currentFacts,
            List<ScopeRun> committedScopes,
            List<FactRecord> committedFacts) {
        Set<OwnerTuple> touchedOwners = new HashSet<>();
        Set<ScopeOwner> openIdentityScopes = new HashSet<>();
        for (ScopeRun scopeRun : committedScopes) {
            if (scopeRun.identityTypeDeclared()) {
                touchedOwners.add(OwnerTuple.from(scopeRun));
            } else {
                openIdentityScopes.add(ScopeOwner.from(scopeRun));
            }
        }
        for (FactRecord fact : currentFacts) {
            if (openIdentityScopes.contains(ScopeOwner.from(fact))) {
                touchedOwners.add(OwnerTuple.from(fact));
            }
        }
        for (FactRecord fact : committedFacts) {
            if (openIdentityScopes.contains(ScopeOwner.from(fact))) {
                touchedOwners.add(OwnerTuple.from(fact));
            }
        }
        Set<String> committedFactKeys = new HashSet<>();
        for (FactRecord fact : committedFacts) {
            committedFactKeys.add(fact.factKey());
        }
        Map<String, FactRecord> merged = new LinkedHashMap<>();
        for (FactRecord fact : currentFacts) {
            if (touchedOwners.contains(OwnerTuple.from(fact)) && !committedFactKeys.contains(fact.factKey())) {
                continue;
            }
            merged.put(fact.factKey(), fact);
        }
        for (FactRecord fact : committedFacts) {
            merged.put(fact.factKey(), fact);
        }
        return List.copyOf(merged.values());
    }

    private static List<RelationFamily> affectedRelationFamilies(List<ScopeRun> committedScopes) {
        Map<RelationFamily, RelationFamily> affected = new LinkedHashMap<>();
        for (ScopeRun scopeRun : committedScopes) {
            affected.putIfAbsent(scopeRun.relationFamily(), scopeRun.relationFamily());
        }
        return List.copyOf(affected.keySet());
    }

    private void requestCacheRebuild(CommittedFactBatch committed, int activeFactCount) {
        try {
            cacheRebuildListener.requestRebuild(new CacheRebuildRequest(
                    committed.analysisRun().projectId(),
                    committed.analysisRun().snapshotId(),
                    affectedRelationFamilies(committed.scopeRuns()),
                    activeFactCount));
        } catch (RuntimeException ignored) {
            // Derived cache rebuild is best-effort here; active facts remain the source of truth.
        }
    }

    private static void requireRunningAnalysisRun(AnalysisRun analysisRun) {
        if (analysisRun == null) {
            throw new IllegalArgumentException("analysisRun is required");
        }
        if (analysisRun.status() != RunStatus.RUNNING) {
            throw new IllegalStateException("analysisRun must be RUNNING before staging");
        }
    }

    private static Map<String, Evidence> validateEvidence(List<Evidence> evidence, ScopeIndex scopeIndex) {
        Map<String, Evidence> evidenceByKey = new LinkedHashMap<>();
        for (Evidence item : evidence) {
            if (item == null) {
                throw new IllegalArgumentException("evidence cannot contain null items");
            }
            if (!scopeIndex.hasAnalyzerScope(item.analyzerId(), item.scopeKey())) {
                throw new IllegalArgumentException("evidence must belong to a declared scope");
            }
            if (evidenceByKey.put(item.evidenceKey(), item) != null) {
                throw new IllegalArgumentException("evidence keys must be unique within a staged batch");
            }
        }
        return Map.copyOf(evidenceByKey);
    }

    private static void validateFacts(
            List<FactRecord> facts,
            AnalysisRun analysisRun,
            ScopeIndex scopeIndex,
            Map<String, Evidence> evidenceByKey) {
        Set<String> factKeys = new HashSet<>();
        for (FactRecord fact : facts) {
            if (fact == null) {
                throw new IllegalArgumentException("facts cannot contain null items");
            }
            if (!fact.analysisRunId().equals(analysisRun.analysisRunId())) {
                throw new IllegalArgumentException("fact analysisRunId must match analysisRun");
            }
            if (!fact.projectId().equals(analysisRun.projectId())) {
                throw new IllegalArgumentException("fact projectId must match analysisRun");
            }
            if (!fact.snapshotId().equals(analysisRun.snapshotId())) {
                throw new IllegalArgumentException("fact snapshotId must match analysisRun");
            }
            ScopeRun scopeRun = scopeIndex.require(fact.scopeRunId());
            if (!scopeRun.analyzerId().equals(fact.analyzerId())
                    || !scopeRun.scopeKey().equals(fact.scopeKey())
                    || scopeRun.relationFamily() != fact.relationFamily()
                    || (scopeRun.identityTypeDeclared() && scopeRun.identityType() != fact.identityType())) {
                throw new IllegalArgumentException("fact must match its declared scope boundary");
            }
            Evidence evidence = evidenceByKey.get(fact.evidenceKey());
            if (evidence == null) {
                throw new IllegalArgumentException("fact must reference staged evidence");
            }
            if (!evidence.analyzerId().equals(fact.analyzerId()) || !evidence.scopeKey().equals(fact.scopeKey())) {
                throw new IllegalArgumentException("fact evidence must match the fact analyzer and scope");
            }
            if (!factKeys.add(fact.factKey())) {
                throw new IllegalArgumentException("fact keys must be unique within a staged batch");
            }
        }
    }

    private static <T> List<T> copyRequired(List<T> values, String name) {
        if (values == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return List.copyOf(values);
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private record ScopeIndex(Map<String, ScopeRun> byScopeRunId, Set<String> analyzerScopes) {
        static ScopeIndex from(AnalysisRun analysisRun, List<ScopeRun> scopeRuns) {
            Map<String, ScopeRun> byScopeRunId = new LinkedHashMap<>();
            Set<String> analyzerScopes = new HashSet<>();
            for (ScopeRun scopeRun : scopeRuns) {
                if (scopeRun == null) {
                    throw new IllegalArgumentException("scopeRuns cannot contain null items");
                }
                if (scopeRun.status() != RunStatus.RUNNING) {
                    throw new IllegalStateException("scopeRuns must be RUNNING before staging");
                }
                if (!scopeRun.analysisRunId().equals(analysisRun.analysisRunId())) {
                    throw new IllegalArgumentException("scopeRun analysisRunId must match analysisRun");
                }
                if (byScopeRunId.put(scopeRun.scopeRunId(), scopeRun) != null) {
                    throw new IllegalArgumentException("scopeRun ids must be unique within a staged batch");
                }
                analyzerScopes.add(analyzerScopeKey(scopeRun.analyzerId(), scopeRun.scopeKey()));
            }
            return new ScopeIndex(Map.copyOf(byScopeRunId), Set.copyOf(analyzerScopes));
        }

        ScopeRun require(String scopeRunId) {
            ScopeRun scopeRun = byScopeRunId.get(scopeRunId);
            if (scopeRun == null) {
                throw new IllegalArgumentException("fact must belong to a declared scopeRun");
            }
            return scopeRun;
        }

        boolean hasAnalyzerScope(String analyzerId, String scopeKey) {
            return analyzerScopes.contains(analyzerScopeKey(analyzerId, scopeKey));
        }

        private static String analyzerScopeKey(String analyzerId, String scopeKey) {
            return analyzerId + "\n" + scopeKey;
        }
    }

    private record OwnerTuple(
            String analyzerId,
            String scopeKey,
            RelationFamily relationFamily,
            IdentityType identityType) {
        static OwnerTuple from(ScopeRun scopeRun) {
            return new OwnerTuple(
                    scopeRun.analyzerId(),
                    scopeRun.scopeKey(),
                    scopeRun.relationFamily(),
                    scopeRun.identityType());
        }

        static OwnerTuple from(FactRecord fact) {
            return new OwnerTuple(fact.analyzerId(), fact.scopeKey(), fact.relationFamily(), fact.identityType());
        }
    }

    private record ScopeOwner(
            String analyzerId,
            String scopeKey,
            RelationFamily relationFamily) {
        static ScopeOwner from(ScopeRun scopeRun) {
            return new ScopeOwner(scopeRun.analyzerId(), scopeRun.scopeKey(), scopeRun.relationFamily());
        }

        static ScopeOwner from(FactRecord fact) {
            return new ScopeOwner(fact.analyzerId(), fact.scopeKey(), fact.relationFamily());
        }
    }
}
