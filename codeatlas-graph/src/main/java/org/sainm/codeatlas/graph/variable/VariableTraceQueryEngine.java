package org.sainm.codeatlas.graph.variable;

import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.FactKey;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.model.SymbolKind;
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

public final class VariableTraceQueryEngine {
    private static final Set<RelationType> SOURCE_RELATIONS = EnumSet.of(
        RelationType.WRITES_PARAM,
        RelationType.DECLARES
    );
    private static final Set<RelationType> SINK_RELATIONS = EnumSet.of(
        RelationType.READS_PARAM,
        RelationType.PASSES_PARAM,
        RelationType.BINDS_TO,
        RelationType.COVERED_BY,
        RelationType.READS_TABLE,
        RelationType.WRITES_TABLE
    );

    public List<VariableTracePath> findSourcePaths(
        List<ActiveFact> activeFacts,
        SymbolId parameter,
        int maxDepth,
        int maxPaths
    ) {
        return trace(activeFacts, parameter, VariableTraceDirection.SOURCE, maxDepth, maxPaths);
    }

    public List<VariableTracePath> findSinkPaths(
        List<ActiveFact> activeFacts,
        SymbolId parameter,
        int maxDepth,
        int maxPaths
    ) {
        return trace(activeFacts, parameter, VariableTraceDirection.SINK, maxDepth, maxPaths);
    }

    private List<VariableTracePath> trace(
        List<ActiveFact> activeFacts,
        SymbolId parameter,
        VariableTraceDirection direction,
        int maxDepth,
        int maxPaths
    ) {
        Objects.requireNonNull(activeFacts, "activeFacts");
        Objects.requireNonNull(parameter, "parameter");
        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth must be positive");
        }
        if (maxPaths < 1) {
            throw new IllegalArgumentException("maxPaths must be positive");
        }

        Map<SymbolId, List<TraceEdge>> adjacency = direction == VariableTraceDirection.SOURCE
            ? sourceAdjacency(activeFacts)
            : sinkAdjacency(activeFacts);
        ArrayDeque<SearchPath> queue = new ArrayDeque<>();
        queue.add(new SearchPath(parameter, List.of(), Set.of(parameter.value())));

        List<VariableTracePath> paths = new ArrayList<>();
        while (!queue.isEmpty() && paths.size() < maxPaths) {
            SearchPath current = queue.removeFirst();
            if (!current.edges().isEmpty()) {
                paths.add(toTracePath(direction, parameter, current, false));
            }
            if (current.edges().size() >= maxDepth) {
                continue;
            }
            for (TraceEdge edge : adjacency.getOrDefault(current.current(), List.of())) {
                SymbolId next = edge.next();
                if (current.visited().contains(next.value())) {
                    continue;
                }
                List<TraceEdge> nextEdges = new ArrayList<>(current.edges());
                nextEdges.add(edge);
                Set<String> nextVisited = new HashSet<>(current.visited());
                nextVisited.add(next.value());
                queue.addLast(new SearchPath(next, nextEdges, nextVisited));
            }
        }
        return paths;
    }

    private Map<SymbolId, List<TraceEdge>> sourceAdjacency(List<ActiveFact> activeFacts) {
        Map<SymbolId, List<TraceEdge>> adjacency = new HashMap<>();
        for (ActiveFact activeFact : activeFacts) {
            FactKey factKey = activeFact.factKey();
            if (!SOURCE_RELATIONS.contains(factKey.relationType())) {
                continue;
            }
            if (factKey.relationType() == RelationType.DECLARES && !isJspDeclaration(factKey)) {
                continue;
            }
            adjacency.computeIfAbsent(factKey.target(), ignored -> new ArrayList<>())
                .add(new TraceEdge(factKey.target(), factKey.source(), activeFact));
        }
        sort(adjacency);
        return adjacency;
    }

    private Map<SymbolId, List<TraceEdge>> sinkAdjacency(List<ActiveFact> activeFacts) {
        Map<SymbolId, List<TraceEdge>> adjacency = new HashMap<>();
        for (ActiveFact activeFact : activeFacts) {
            FactKey factKey = activeFact.factKey();
            if (!SINK_RELATIONS.contains(factKey.relationType())) {
                continue;
            }
            if (factKey.relationType() == RelationType.READS_PARAM) {
                adjacency.computeIfAbsent(factKey.target(), ignored -> new ArrayList<>())
                    .add(new TraceEdge(factKey.target(), factKey.source(), activeFact));
            } else {
                adjacency.computeIfAbsent(factKey.source(), ignored -> new ArrayList<>())
                    .add(new TraceEdge(factKey.source(), factKey.target(), activeFact));
            }
        }
        sort(adjacency);
        return adjacency;
    }

    private void sort(Map<SymbolId, List<TraceEdge>> adjacency) {
        adjacency.values().forEach(edges -> edges.sort(Comparator
            .comparing((TraceEdge edge) -> edge.next().value())
            .thenComparing(edge -> edge.fact().factKey().relationType().name())
            .thenComparing(edge -> edge.fact().factKey().qualifier())));
    }

    private VariableTracePath toTracePath(
        VariableTraceDirection direction,
        SymbolId parameter,
        SearchPath searchPath,
        boolean truncated
    ) {
        List<VariableTraceStep> steps = new ArrayList<>();
        ActiveFact firstFact = searchPath.edges().getFirst().fact();
        steps.add(new VariableTraceStep(parameter, null, "", firstSourceType(firstFact), Confidence.CERTAIN, List.of()));
        for (TraceEdge edge : searchPath.edges()) {
            ActiveFact fact = edge.fact();
            FactKey factKey = fact.factKey();
            steps.add(new VariableTraceStep(
                edge.next(),
                factKey.relationType(),
                factKey.qualifier(),
                firstSourceType(fact),
                fact.confidence(),
                fact.evidenceKeys()
            ));
        }
        return VariableTracePath.fromSteps(direction, parameter, searchPath.current(), steps, truncated);
    }

    private SourceType firstSourceType(ActiveFact activeFact) {
        return activeFact.sourceTypes().stream()
            .min(Comparator.comparing(Enum::name))
            .orElse(SourceType.STATIC_RULE);
    }

    private boolean isJspDeclaration(FactKey factKey) {
        return isJspArtifact(factKey.source().kind()) && isJspArtifact(factKey.target().kind());
    }

    private boolean isJspArtifact(SymbolKind kind) {
        return kind == SymbolKind.JSP_PAGE
            || kind == SymbolKind.JSP_FORM
            || kind == SymbolKind.JSP_INPUT;
    }

    private record TraceEdge(
        SymbolId current,
        SymbolId next,
        ActiveFact fact
    ) {
    }

    private record SearchPath(
        SymbolId current,
        List<TraceEdge> edges,
        Set<String> visited
    ) {
    }
}
