package org.sainm.codeatlas.graph;

public record Neo4jIdentityConstraint(String name, String label, String property) {
    public Neo4jIdentityConstraint {
        GraphNodeType.requireNonBlank(name, "name");
        GraphNodeType.requireLabel(label);
        GraphNodeType.requireNonBlank(property, "property");
    }

    public String cypher() {
        return "CREATE CONSTRAINT " + name
                + " IF NOT EXISTS FOR (n:" + label + ") REQUIRE n." + property + " IS UNIQUE";
    }
}
