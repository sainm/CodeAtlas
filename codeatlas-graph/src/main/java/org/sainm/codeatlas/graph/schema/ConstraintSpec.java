package org.sainm.codeatlas.graph.schema;

import java.util.List;
import java.util.Objects;

public record ConstraintSpec(
    String name,
    String label,
    List<String> properties,
    boolean unique
) {
    public ConstraintSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(properties, "properties");
        if (name.isBlank() || label.isBlank() || properties.isEmpty()) {
            throw new IllegalArgumentException("constraint name, label and properties must be present");
        }
        properties = List.copyOf(properties);
    }
}
