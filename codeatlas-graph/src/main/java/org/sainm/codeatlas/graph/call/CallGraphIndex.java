package org.sainm.codeatlas.graph.call;

import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.store.ActiveFact;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CallGraphIndex {
    private final Map<SymbolId, List<CallEdge>> callersByCallee = new HashMap<>();
    private final Map<SymbolId, List<CallEdge>> calleesByCaller = new HashMap<>();

    private CallGraphIndex() {
    }

    public static CallGraphIndex fromActiveFacts(List<ActiveFact> activeFacts) {
        CallGraphIndex index = new CallGraphIndex();
        activeFacts.stream()
            .filter(fact -> fact.factKey().relationType() == RelationType.CALLS)
            .map(fact -> new CallEdge(
                fact.factKey().source(),
                fact.factKey().target(),
                fact.confidence(),
                fact.sourceTypes()
            ))
            .forEach(index::add);
        index.sort();
        return index;
    }

    public List<CallEdge> callers(SymbolId callee) {
        return callersByCallee.getOrDefault(callee, List.of());
    }

    public List<CallEdge> callees(SymbolId caller) {
        return calleesByCaller.getOrDefault(caller, List.of());
    }

    public int edgeCount() {
        return calleesByCaller.values().stream().mapToInt(List::size).sum();
    }

    private void add(CallEdge edge) {
        callersByCallee.computeIfAbsent(edge.callee(), ignored -> new ArrayList<>()).add(edge);
        calleesByCaller.computeIfAbsent(edge.caller(), ignored -> new ArrayList<>()).add(edge);
    }

    private void sort() {
        Comparator<CallEdge> byCaller = Comparator.comparing(edge -> edge.caller().value());
        Comparator<CallEdge> byCallee = Comparator.comparing(edge -> edge.callee().value());
        callersByCallee.values().forEach(list -> list.sort(byCaller));
        calleesByCaller.values().forEach(list -> list.sort(byCallee));
    }
}
