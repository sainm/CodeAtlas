package org.sainm.codeatlas.graph;

import java.util.List;

public record Neo4jPropertyIndex(String name, String targetPattern, String variable, List<String> properties) {
    public Neo4jPropertyIndex {
        GraphNodeType.requireNonBlank(name, "name");
        GraphNodeType.requireNonBlank(targetPattern, "targetPattern");
        GraphNodeType.requireNonBlank(variable, "variable");
        if (properties == null || properties.isEmpty()) {
            throw new IllegalArgumentException("properties are required");
        }
        properties = List.copyOf(properties);
        for (String property : properties) {
            GraphNodeType.requireNonBlank(property, "property");
        }
    }

    public String cypher() {
        return "CREATE INDEX " + name
                + " IF NOT EXISTS FOR " + targetPattern
                + " ON (" + propertyList() + ")";
    }

    private String propertyList() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < properties.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(variable).append(".").append(properties.get(i));
        }
        return builder.toString();
    }
}
