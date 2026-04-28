package org.sainm.codeatlas.analyzers.java;

import org.sainm.codeatlas.graph.model.SymbolId;

public record VariableEvent(
    SymbolId methodSymbol,
    String variableName,
    VariableEventKind kind,
    String expression,
    int line
) {
    public VariableEvent {
        if (methodSymbol == null) {
            throw new IllegalArgumentException("methodSymbol is required");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        variableName = variableName == null ? "" : variableName.trim();
        expression = expression == null ? "" : expression.trim();
        line = Math.max(0, line);
    }
}
