package org.sainm.codeatlas.graph.model;

import java.util.Objects;

public record FactKey(
    SymbolId source,
    RelationType relationType,
    SymbolId target,
    String qualifier
) {
    public FactKey {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(relationType, "relationType");
        Objects.requireNonNull(target, "target");
        qualifier = qualifier == null ? "" : qualifier.trim();
    }

    public String value() {
        return source.value() + "|"
            + relationType.name() + "|"
            + target.value() + "|"
            + qualifier;
    }
}

