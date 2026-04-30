package org.sainm.codeatlas.graph.flow;

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

public final class GraphFlowQueryEngine {
    public static final Set<RelationType> CALL_GRAPH_RELATIONS = EnumSet.of(
        RelationType.CALLS,
        RelationType.PASSES_PARAM,
        RelationType.IMPLEMENTS,
        RelationType.BRIDGES_TO
    );

    public static final Set<RelationType> JSP_BACKEND_FLOW_RELATIONS = EnumSet.of(
        RelationType.DECLARES,
        RelationType.SUBMITS_TO,
        RelationType.INCLUDES,
        RelationType.FORWARDS_TO,
        RelationType.ROUTES_TO,
        RelationType.CALLS,
        RelationType.IMPLEMENTS,
        RelationType.INJECTS,
        RelationType.BRIDGES_TO,
        RelationType.BINDS_TO,
        RelationType.READS_PARAM,
        RelationType.WRITES_PARAM,
        RelationType.COVERED_BY,
        RelationType.USES_CONFIG,
        RelationType.EXTENDS,
        RelationType.READS_TABLE,
        RelationType.WRITES_TABLE
    );

    public List<GraphFlowPath> findDownstreamPaths(
        List<ActiveFact> activeFacts,
        SymbolId start,
        Set<RelationType> relationTypes,
        int maxDepth,
        int maxPaths
    ) {
        Objects.requireNonNull(activeFacts, "activeFacts");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(relationTypes, "relationTypes");
        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth must be positive");
        }
        if (maxPaths < 1) {
            throw new IllegalArgumentException("maxPaths must be positive");
        }

        Map<SymbolId, List<ActiveFact>> outgoing = buildOutgoing(activeFacts, relationTypes);
        ArrayDeque<SearchPath> queue = new ArrayDeque<>();
        queue.add(new SearchPath(start, List.of(), Set.of(start.value())));

        List<GraphFlowPath> paths = new ArrayList<>();
        while (!queue.isEmpty() && paths.size() < maxPaths) {
            SearchPath current = queue.removeFirst();
            if (!current.edges().isEmpty()) {
                paths.add(toFlowPath(start, current, false));
            }
            if (current.edges().size() >= maxDepth) {
                continue;
            }
            for (ActiveFact edge : outgoing.getOrDefault(current.current(), List.of())) {
                SymbolId next = edge.factKey().target();
                if (current.visited().contains(next.value())) {
                    continue;
                }
                List<ActiveFact> nextEdges = new ArrayList<>(current.edges());
                nextEdges.add(edge);
                Set<String> nextVisited = new HashSet<>(current.visited());
                nextVisited.add(next.value());
                queue.addLast(new SearchPath(next, nextEdges, nextVisited));
            }
        }
        return paths;
    }

    public List<GraphFlowPath> findUpstreamPaths(
        List<ActiveFact> activeFacts,
        SymbolId start,
        Set<RelationType> relationTypes,
        int maxDepth,
        int maxPaths
    ) {
        Objects.requireNonNull(activeFacts, "activeFacts");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(relationTypes, "relationTypes");
        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth must be positive");
        }
        if (maxPaths < 1) {
            throw new IllegalArgumentException("maxPaths must be positive");
        }

        Map<SymbolId, List<ActiveFact>> incoming = buildIncoming(activeFacts, relationTypes);
        ArrayDeque<SearchPath> queue = new ArrayDeque<>();
        queue.add(new SearchPath(start, List.of(), Set.of(start.value())));

        List<GraphFlowPath> paths = new ArrayList<>();
        while (!queue.isEmpty() && paths.size() < maxPaths) {
            SearchPath current = queue.removeFirst();
            if (!current.edges().isEmpty()) {
                paths.add(toUpstreamFlowPath(start, current, false));
            }
            if (current.edges().size() >= maxDepth) {
                continue;
            }
            for (ActiveFact edge : incoming.getOrDefault(current.current(), List.of())) {
                SymbolId next = edge.factKey().source();
                if (current.visited().contains(next.value())) {
                    continue;
                }
                List<ActiveFact> nextEdges = new ArrayList<>(current.edges());
                nextEdges.add(edge);
                Set<String> nextVisited = new HashSet<>(current.visited());
                nextVisited.add(next.value());
                queue.addLast(new SearchPath(next, nextEdges, nextVisited));
            }
        }
        return paths;
    }

    private Map<SymbolId, List<ActiveFact>> buildOutgoing(List<ActiveFact> activeFacts, Set<RelationType> relationTypes) {
        Map<SymbolId, List<ActiveFact>> outgoing = new HashMap<>();
        for (ActiveFact activeFact : activeFacts) {
            FactKey factKey = activeFact.factKey();
            if (!relationTypes.contains(factKey.relationType())) {
                continue;
            }
            outgoing.computeIfAbsent(factKey.source(), ignored -> new ArrayList<>()).add(activeFact);
        }
        outgoing.values().forEach(edges -> edges.sort(Comparator
            .comparing((ActiveFact edge) -> edge.factKey().target().value())
            .thenComparing(edge -> edge.factKey().relationType().name())
            .thenComparing(edge -> edge.factKey().qualifier())));
        return outgoing;
    }

    private Map<SymbolId, List<ActiveFact>> buildIncoming(List<ActiveFact> activeFacts, Set<RelationType> relationTypes) {
        Map<SymbolId, List<ActiveFact>> incoming = new HashMap<>();
        for (ActiveFact activeFact : activeFacts) {
            FactKey factKey = activeFact.factKey();
            if (!relationTypes.contains(factKey.relationType())) {
                continue;
            }
            incoming.computeIfAbsent(factKey.target(), ignored -> new ArrayList<>()).add(activeFact);
        }
        incoming.values().forEach(edges -> edges.sort(Comparator
            .comparing((ActiveFact edge) -> edge.factKey().source().value())
            .thenComparing(edge -> edge.factKey().relationType().name())
            .thenComparing(edge -> edge.factKey().qualifier())));
        return incoming;
    }

    private GraphFlowPath toFlowPath(SymbolId start, SearchPath searchPath, boolean truncated) {
        List<GraphFlowStep> steps = new ArrayList<>();
        ActiveFact firstFact = searchPath.edges().getFirst();
        steps.add(new GraphFlowStep(start, null, "", firstSourceType(firstFact), Confidence.CERTAIN, List.of()));
        for (ActiveFact edge : searchPath.edges()) {
            FactKey factKey = edge.factKey();
            steps.add(new GraphFlowStep(
                factKey.target(),
                factKey.relationType(),
                factKey.qualifier(),
                firstSourceType(edge),
                edge.confidence(),
                edge.evidenceKeys()
            ));
        }
        return GraphFlowPath.fromSteps(start, searchPath.current(), steps, truncated);
    }

    private GraphFlowPath toUpstreamFlowPath(SymbolId start, SearchPath searchPath, boolean truncated) {
        List<GraphFlowStep> steps = new ArrayList<>();
        ActiveFact firstFact = searchPath.edges().getFirst();
        steps.add(new GraphFlowStep(start, null, "", firstSourceType(firstFact), Confidence.CERTAIN, List.of()));
        for (ActiveFact edge : searchPath.edges()) {
            FactKey factKey = edge.factKey();
            steps.add(new GraphFlowStep(
                factKey.source(),
                factKey.relationType(),
                factKey.qualifier(),
                firstSourceType(edge),
                edge.confidence(),
                edge.evidenceKeys()
            ));
        }
        return GraphFlowPath.fromSteps(start, searchPath.current(), steps, truncated);
    }

    private SourceType firstSourceType(ActiveFact activeFact) {
        return activeFact.sourceTypes().stream()
            .min(Comparator.comparing(Enum::name))
            .orElse(SourceType.STATIC_RULE);
    }

    private record SearchPath(
        SymbolId current,
        List<ActiveFact> edges,
        Set<String> visited
    ) {
    }
}
