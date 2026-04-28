package org.sainm.codeatlas.graph.call;

import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import java.util.Set;

public record CallEdge(
    SymbolId caller,
    SymbolId callee,
    Confidence confidence,
    Set<SourceType> sourceTypes
) {
    public CallEdge {
        if (caller == null) {
            throw new IllegalArgumentException("caller is required");
        }
        if (callee == null) {
            throw new IllegalArgumentException("callee is required");
        }
        if (confidence == null) {
            throw new IllegalArgumentException("confidence is required");
        }
        sourceTypes = Set.copyOf(sourceTypes);
    }
}
