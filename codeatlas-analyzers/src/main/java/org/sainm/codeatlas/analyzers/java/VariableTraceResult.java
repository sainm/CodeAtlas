package org.sainm.codeatlas.analyzers.java;

import java.util.Comparator;
import java.util.List;

public record VariableTraceResult(
    List<VariableEvent> events,
    List<MethodArgumentFlowEvent> argumentFlows
) {
    public VariableTraceResult(List<VariableEvent> events) {
        this(events, List.of());
    }

    public VariableTraceResult {
        events = events.stream()
            .sorted(Comparator.comparing(VariableEvent::methodSymbol, Comparator.comparing(symbol -> symbol.value()))
                .thenComparingInt(VariableEvent::line)
                .thenComparing(VariableEvent::kind)
                .thenComparing(VariableEvent::variableName))
            .toList();
        argumentFlows = argumentFlows.stream()
            .sorted(Comparator.comparing(MethodArgumentFlowEvent::callerMethodSymbol, Comparator.comparing(symbol -> symbol.value()))
                .thenComparingInt(MethodArgumentFlowEvent::line)
                .thenComparing(flow -> flow.calleeMethodSymbol().value())
                .thenComparing(MethodArgumentFlowEvent::argumentName))
            .toList();
    }

    public List<VariableEvent> eventsFor(String variableName) {
        return events.stream()
            .filter(event -> event.variableName().equals(variableName))
            .toList();
    }
}
