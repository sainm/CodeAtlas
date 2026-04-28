package org.sainm.codeatlas.graph.schema;

import java.util.Objects;

public record PropertySpec(
    String name,
    PropertyType type,
    boolean required
) {
    public PropertySpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        if (name.isBlank()) {
            throw new IllegalArgumentException("property name must not be blank");
        }
    }
}
