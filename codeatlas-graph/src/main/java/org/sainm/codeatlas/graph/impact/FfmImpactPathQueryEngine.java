package org.sainm.codeatlas.graph.impact;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.FactKey;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.offheap.CompressedGraphEdge;
import org.sainm.codeatlas.graph.offheap.OffHeapGraphIndex;
import org.sainm.codeatlas.graph.store.ActiveFact;

public final class FfmImpactPathQueryEngine {
    private static final Set<RelationType> UPSTREAM_RELATIONS = EnumSet.of(
        RelationType.CALLS,
        RelationType.ROUTES_TO,
        RelationType.SUBMITS_TO,
        RelationType.INCLUDES,
        RelationType.BINDS_TO,
        RelationType.FORWARDS_TO,
        RelationType.READS_TABLE,
        RelationType.WRITES_TABLE
    );

    public List<ImpactPath> findUpstreamImpactPaths(
        List<ActiveFact> activeFacts,
        SymbolId changedSymbol,
        Predicate<SymbolId> entrypointPredicate,
        int maxDepth,
        int maxPaths
    ) {
        Objects.requireNonNull(activeFacts, "activeFacts");
        Objects.requireNonNull(changedSymbol, "changedSymbol");
        Objects.requireNonNull(entrypointPredicate, "entrypointPredicate");
        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth must be positive");
        }
        if (maxPaths < 1) {
            throw new IllegalArgumentException("maxPaths must be positive");
        }
        CompressedImpactGraph compressed = compress(activeFacts, changedSymbol);
        Integer changedNodeId = compressed.nodeIds().get(changedSymbol);
        if (changedNodeId == null) {
            return List.of();
        }
        try (OffHeapGraphIndex index = OffHeapGraphIndex.confined(compressed.symbols().size(), compressed.edges())) {
            return findPaths(index, compressed, changedSymbol, changedNodeId, entrypointPredicate, maxDepth, maxPaths);
        }
    }

    private List<ImpactPath> findPaths(
        OffHeapGraphIndex index,
        CompressedImpactGraph compressed,
        SymbolId changedSymbol,
        int changedNodeId,
        Predicate<SymbolId> entrypointPredicate,
        int maxDepth,
        int maxPaths
    ) {
        ArrayDeque<SearchPath> queue = new ArrayDeque<>();
        queue.add(new SearchPath(changedNodeId, List.of(), Set.of(changedNodeId)));
        List<ImpactPath> paths = new ArrayList<>();
        while (!queue.isEmpty() && paths.size() < maxPaths) {
            SearchPath current = queue.removeFirst();
            SymbolId currentSymbol = compressed.symbols().get(current.currentNodeId());
            if (!current.edgePath().isEmpty() && entrypointPredicate.test(currentSymbol)) {
                paths.add(toImpactPath(current, compressed, changedSymbol));
                continue;
            }
            if (current.edgePath().size() >= maxDepth) {
                continue;
            }
            for (int callerId : index.callersOf(current.currentNodeId())) {
                if (current.visitedNodeIds().contains(callerId)) {
                    continue;
                }
                ActiveFact edge = compressed.factByEndpoints().get(endpointKey(callerId, current.currentNodeId()));
                if (edge == null) {
                    continue;
                }
                List<ActiveFact> nextEdges = new ArrayList<>(current.edgePath());
                nextEdges.add(edge);
                java.util.LinkedHashSet<Integer> visited = new java.util.LinkedHashSet<>(current.visitedNodeIds());
                visited.add(callerId);
                queue.addLast(new SearchPath(callerId, nextEdges, visited));
            }
        }
        return paths;
    }

    private CompressedImpactGraph compress(List<ActiveFact> activeFacts, SymbolId changedSymbol) {
        Map<SymbolId, Integer> nodeIds = new LinkedHashMap<>();
        List<SymbolId> symbols = new ArrayList<>();
        List<CompressedGraphEdge> edges = new ArrayList<>();
        Map<String, ActiveFact> factByEndpoints = new LinkedHashMap<>();
        int edgeTypeId = 0;
        for (ActiveFact activeFact : activeFacts) {
            FactKey factKey = activeFact.factKey();
            if (!UPSTREAM_RELATIONS.contains(factKey.relationType())) {
                continue;
            }
            int sourceId = nodeId(factKey.source(), nodeIds, symbols);
            int targetId = nodeId(factKey.target(), nodeIds, symbols);
            edges.add(new CompressedGraphEdge(sourceId, targetId, edgeTypeId++));
            factByEndpoints.putIfAbsent(endpointKey(sourceId, targetId), activeFact);
        }
        nodeId(changedSymbol, nodeIds, symbols);
        return new CompressedImpactGraph(nodeIds, symbols, edges, factByEndpoints);
    }

    private int nodeId(SymbolId symbolId, Map<SymbolId, Integer> nodeIds, List<SymbolId> symbols) {
        Integer existing = nodeIds.get(symbolId);
        if (existing != null) {
            return existing;
        }
        int next = symbols.size();
        nodeIds.put(symbolId, next);
        symbols.add(symbolId);
        return next;
    }

    private ImpactPath toImpactPath(SearchPath searchPath, CompressedImpactGraph compressed, SymbolId changedSymbol) {
        List<ActiveFact> forwardEdges = new ArrayList<>(searchPath.edgePath());
        java.util.Collections.reverse(forwardEdges);
        List<ImpactPathStep> steps = new ArrayList<>();
        ActiveFact firstEdge = forwardEdges.getFirst();
        SymbolId entrypoint = firstEdge.factKey().source();
        steps.add(new ImpactPathStep(entrypoint, null, firstSourceType(firstEdge), firstEdge.confidence(), List.of()));
        for (ActiveFact edge : forwardEdges) {
            steps.add(new ImpactPathStep(
                edge.factKey().target(),
                edge.factKey().relationType(),
                firstSourceType(edge),
                edge.confidence(),
                edge.evidenceKeys()
            ));
        }
        return ImpactPath.fromSteps(
            entrypoint,
            changedSymbol,
            steps,
            inferRisk(forwardEdges),
            "Entrypoint reaches changed symbol through benchmark-enabled FFM graph index.",
            false
        );
    }

    private RiskLevel inferRisk(List<ActiveFact> forwardEdges) {
        boolean hasCertain = forwardEdges.stream().anyMatch(edge -> edge.confidence() == Confidence.CERTAIN);
        boolean hasLikely = forwardEdges.stream().anyMatch(edge -> edge.confidence() == Confidence.LIKELY);
        if (hasCertain && hasLikely) {
            return RiskLevel.HIGH;
        }
        if (hasCertain || hasLikely) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private SourceType firstSourceType(ActiveFact activeFact) {
        return activeFact.sourceTypes().stream()
            .min(Comparator.comparing(Enum::name))
            .orElse(SourceType.STATIC_RULE);
    }

    private String endpointKey(int sourceId, int targetId) {
        return sourceId + "->" + targetId;
    }

    private record SearchPath(
        int currentNodeId,
        List<ActiveFact> edgePath,
        Set<Integer> visitedNodeIds
    ) {
    }

    private record CompressedImpactGraph(
        Map<SymbolId, Integer> nodeIds,
        List<SymbolId> symbols,
        List<CompressedGraphEdge> edges,
        Map<String, ActiveFact> factByEndpoints
    ) {
    }
}
