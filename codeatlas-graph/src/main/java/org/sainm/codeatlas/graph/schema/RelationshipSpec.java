package org.sainm.codeatlas.graph.schema;

import org.sainm.codeatlas.graph.model.RelationType;
import java.util.List;
import java.util.Objects;

public record RelationshipSpec(
    RelationType type,
    List<PropertySpec> properties
) {
    public RelationshipSpec {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(properties, "properties");
        properties = List.copyOf(properties);
    }
}
