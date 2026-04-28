package org.sainm.codeatlas.graph.store;

import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.EvidenceKey;
import org.sainm.codeatlas.graph.model.FactKey;
import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public final class InMemoryGraphRepository implements GraphRepository {
    private final Map<String, GraphNode> nodes = new HashMap<>();
    private final Map<String, List<GraphFact>> factsByProject = new HashMap<>();

    @Override
    public synchronized GraphNode upsertNode(GraphNode node) {
        String key = node.symbolId().value();
        GraphNode merged = nodes.containsKey(key) ? nodes.get(key).merge(node) : node;
        nodes.put(key, merged);
        return merged;
    }

    @Override
    public synchronized Optional<GraphNode> findNode(SymbolId symbolId) {
        return Optional.ofNullable(nodes.get(symbolId.value()));
    }

    @Override
    public synchronized void upsertFact(GraphFact fact) {
        factsByProject.computeIfAbsent(fact.projectId(), ignored -> new ArrayList<>()).add(fact);
    }

    @Override
    public synchronized void reanalyzeScope(
        String projectId,
        String previousSnapshotId,
        String newSnapshotId,
        String analysisRunId,
        String scopeKey,
        Collection<GraphFact> emittedFacts
    ) {
        Set<String> emittedFactEvidenceKeys = new HashSet<>();
        for (GraphFact fact : emittedFacts) {
            if (!fact.projectId().equals(projectId)
                || !fact.snapshotId().equals(newSnapshotId)
                || !fact.analysisRunId().equals(analysisRunId)
                || !fact.scopeKey().equals(scopeKey)) {
                throw new IllegalArgumentException("emitted fact does not match reanalyzed scope");
            }
            emittedFactEvidenceKeys.add(factEvidenceKey(fact));
            upsertFact(fact);
        }

        for (GraphFact activeEvidence : activeEvidenceFacts(projectId, previousSnapshotId)) {
            boolean sameScope = activeEvidence.scopeKey().equals(scopeKey);
            if (sameScope && !emittedFactEvidenceKeys.contains(factEvidenceKey(activeEvidence))) {
                upsertFact(activeEvidence.tombstone(newSnapshotId, analysisRunId));
            }
        }
    }

    @Override
    public synchronized List<ActiveFact> activeFacts(String projectId, String snapshotId) {
        Map<FactKey, List<GraphFact>> activeByFact = new LinkedHashMap<>();
        for (GraphFact fact : activeEvidenceFacts(projectId, snapshotId)) {
            activeByFact.computeIfAbsent(fact.factKey(), ignored -> new ArrayList<>()).add(fact);
        }
        return activeByFact.entrySet().stream()
            .map(entry -> toActiveFact(entry.getKey(), entry.getValue()))
            .flatMap(Optional::stream)
            .sorted(Comparator.comparing(fact -> fact.factKey().value()))
            .toList();
    }

    @Override
    public synchronized Optional<ActiveFact> activeFact(String projectId, String snapshotId, FactKey factKey) {
        return activeFacts(projectId, snapshotId).stream()
            .filter(fact -> fact.factKey().equals(factKey))
            .findFirst();
    }

    @Override
    public synchronized SnapshotDiff diff(String projectId, String leftSnapshotId, String rightSnapshotId) {
        Set<FactKey> left = new HashSet<>(activeFacts(projectId, leftSnapshotId).stream().map(ActiveFact::factKey).toList());
        Set<FactKey> right = new HashSet<>(activeFacts(projectId, rightSnapshotId).stream().map(ActiveFact::factKey).toList());

        Set<FactKey> added = new HashSet<>(right);
        added.removeAll(left);
        Set<FactKey> removed = new HashSet<>(left);
        removed.removeAll(right);
        Set<FactKey> retained = new HashSet<>(left);
        retained.retainAll(right);
        return new SnapshotDiff(added, removed, retained);
    }

    private List<GraphFact> factsFor(String projectId) {
        return factsByProject.getOrDefault(projectId, List.of());
    }

    private List<GraphFact> activeEvidenceFacts(String projectId, String snapshotId) {
        Map<String, GraphFact> latestByEvidence = new LinkedHashMap<>();
        for (GraphFact fact : factsFor(projectId)) {
            if (fact.snapshotId().compareTo(snapshotId) > 0) {
                continue;
            }
            String evidenceKey = factEvidenceKey(fact);
            GraphFact current = latestByEvidence.get(evidenceKey);
            if (current == null || current.snapshotId().compareTo(fact.snapshotId()) <= 0) {
                latestByEvidence.put(evidenceKey, fact);
            }
        }
        return latestByEvidence.values().stream()
            .filter(GraphFact::active)
            .filter(fact -> !fact.tombstone())
            .toList();
    }

    private Optional<ActiveFact> toActiveFact(FactKey factKey, List<GraphFact> candidates) {
        List<GraphFact> activeCandidates = candidates.stream()
            .filter(GraphFact::active)
            .filter(candidate -> !candidate.tombstone())
            .toList();
        if (activeCandidates.isEmpty()) {
            return Optional.empty();
        }

        List<EvidenceKey> evidenceKeys = activeCandidates.stream()
            .map(GraphFact::evidenceKey)
            .distinct()
            .sorted(Comparator.comparing(EvidenceKey::value))
            .toList();
        Set<SourceType> sourceTypes = new TreeSet<>(activeCandidates.stream().map(GraphFact::sourceType).toList());
        Confidence confidence = activeCandidates.stream()
            .map(GraphFact::confidence)
            .reduce(Confidence.UNKNOWN, Confidence::max);
        return Optional.of(new ActiveFact(factKey, evidenceKeys, sourceTypes, confidence));
    }

    private String factEvidenceKey(GraphFact fact) {
        return fact.factKey().value() + "|" + fact.evidenceKey().value();
    }
}
