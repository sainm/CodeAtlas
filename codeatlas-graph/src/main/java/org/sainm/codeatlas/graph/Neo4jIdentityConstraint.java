package org.sainm.codeatlas.graph;

import java.util.List;

public record Neo4jIdentityConstraint(String name, String label, List<String> properties) {
    public Neo4jIdentityConstraint {
        GraphNodeType.requireNonBlank(name, "name");
        GraphNodeType.requireLabel(label);
        if (properties == null || properties.isEmpty()) {
            throw new IllegalArgumentException("properties are required");
        }
        for (String property : properties) {
            GraphNodeType.requireNonBlank(property, "property");
        }
        properties = List.copyOf(properties);
    }

    public String cypher() {
        String propertyExpression = properties.size() == 1
                ? "n." + properties.get(0)
                : "(" + properties.stream().map(property -> "n." + property).reduce((left, right) -> left + ", " + right).orElseThrow() + ")";
        return "CREATE CONSTRAINT " + name
                + " IF NOT EXISTS FOR (n:" + label + ") REQUIRE " + propertyExpression + " IS UNIQUE";
    }
}
