package org.sainm.codeatlas.graph.impact;

import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.FactKey;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.store.ActiveFact;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public final class ImpactPathQueryEngine {
    private static final Set<RelationType> DEFAULT_UPSTREAM_RELATIONS = EnumSet.of(
        RelationType.CALLS,
        RelationType.ROUTES_TO,
        RelationType.SUBMITS_TO,
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

        Map<SymbolId, List<ActiveFact>> incoming = buildIncoming(activeFacts);
        ArrayDeque<SearchPath> queue = new ArrayDeque<>();
        queue.add(new SearchPath(changedSymbol, List.of(), Set.of(changedSymbol.value())));

        List<ImpactPath> paths = new ArrayList<>();
        while (!queue.isEmpty() && paths.size() < maxPaths) {
            SearchPath current = queue.removeFirst();
            if (!current.edgePath().isEmpty() && entrypointPredicate.test(current.current())) {
                paths.add(toImpactPath(current, changedSymbol));
                continue;
            }
            if (current.edgePath().size() >= maxDepth) {
                continue;
            }

            for (ActiveFact incomingFact : incoming.getOrDefault(current.current(), List.of())) {
                SymbolId next = incomingFact.factKey().source();
                if (current.visited().contains(next.value())) {
                    continue;
                }
                List<ActiveFact> nextEdges = new ArrayList<>(current.edgePath());
                nextEdges.add(incomingFact);
                Set<String> visited = new HashSet<>(current.visited());
                visited.add(next.value());
                queue.addLast(new SearchPath(next, nextEdges, visited));
            }
        }
        return paths;
    }

    private Map<SymbolId, List<ActiveFact>> buildIncoming(List<ActiveFact> activeFacts) {
        Map<SymbolId, List<ActiveFact>> incoming = new HashMap<>();
        for (ActiveFact activeFact : activeFacts) {
            FactKey factKey = activeFact.factKey();
            if (!DEFAULT_UPSTREAM_RELATIONS.contains(factKey.relationType())) {
                continue;
            }
            incoming.computeIfAbsent(factKey.target(), ignored -> new ArrayList<>()).add(activeFact);
        }
        incoming.values().forEach(list -> list.sort(Comparator.comparing(fact -> fact.factKey().source().value())));
        return incoming;
    }

    private ImpactPath toImpactPath(SearchPath searchPath, SymbolId changedSymbol) {
        List<ActiveFact> forwardEdges = new ArrayList<>(searchPath.edgePath());
        java.util.Collections.reverse(forwardEdges);

        List<ImpactPathStep> steps = new ArrayList<>();
        SymbolId entrypoint = forwardEdges.getFirst().factKey().source();
        ActiveFact firstEdge = forwardEdges.getFirst();
        steps.add(new ImpactPathStep(entrypoint, null, firstSourceType(firstEdge), firstEdge.confidence()));
        for (ActiveFact edge : forwardEdges) {
            steps.add(new ImpactPathStep(
                edge.factKey().target(),
                edge.factKey().relationType(),
                firstSourceType(edge),
                edge.confidence()
            ));
        }

        return ImpactPath.fromSteps(
            entrypoint,
            changedSymbol,
            steps,
            inferRisk(forwardEdges),
            "Entrypoint reaches changed symbol through active graph facts.",
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

    private record SearchPath(
        SymbolId current,
        List<ActiveFact> edgePath,
        Set<String> visited
    ) {
    }
}
