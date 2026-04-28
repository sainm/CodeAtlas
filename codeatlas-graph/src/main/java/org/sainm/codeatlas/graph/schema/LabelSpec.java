package org.sainm.codeatlas.graph.schema;

import java.util.List;
import java.util.Objects;

public record LabelSpec(
    String label,
    List<PropertySpec> properties
) {
    public LabelSpec {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(properties, "properties");
        if (label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
        properties = List.copyOf(properties);
    }
}
