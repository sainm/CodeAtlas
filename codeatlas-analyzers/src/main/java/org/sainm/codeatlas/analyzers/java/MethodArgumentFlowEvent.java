package org.sainm.codeatlas.analyzers.java;

import org.sainm.codeatlas.graph.model.SymbolId;

public record MethodArgumentFlowEvent(
    SymbolId callerMethodSymbol,
    SymbolId calleeMethodSymbol,
    String requestParameterName,
    String argumentName,
    String flowKind,
    String expression,
    int line,
    String sourcePath
) {
    public MethodArgumentFlowEvent(
        SymbolId callerMethodSymbol,
        SymbolId calleeMethodSymbol,
        String requestParameterName,
        String argumentName,
        String flowKind,
        String expression,
        int line
    ) {
        this(callerMethodSymbol, calleeMethodSymbol, requestParameterName, argumentName, flowKind, expression, line, "_unknown");
    }

    public MethodArgumentFlowEvent {
        if (callerMethodSymbol == null) {
            throw new IllegalArgumentException("callerMethodSymbol is required");
        }
        if (calleeMethodSymbol == null) {
            throw new IllegalArgumentException("calleeMethodSymbol is required");
        }
        requestParameterName = requestParameterName == null ? "" : requestParameterName.trim();
        argumentName = argumentName == null ? "" : argumentName.trim();
        flowKind = flowKind == null || flowKind.isBlank() ? "method-argument" : flowKind.trim();
        expression = expression == null ? "" : expression.trim();
        line = Math.max(0, line);
        sourcePath = sourcePath == null || sourcePath.isBlank() ? "_unknown" : sourcePath.trim();
    }
}
