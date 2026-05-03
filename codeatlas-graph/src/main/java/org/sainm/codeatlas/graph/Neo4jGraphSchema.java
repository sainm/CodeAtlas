package org.sainm.codeatlas.graph;

import java.util.ArrayList;
import java.util.List;

public final class Neo4jGraphSchema {
    private final GraphNodeTypeRegistry nodeTypes;
    private final List<Neo4jIdentityConstraint> identityConstraints;
    private final List<Neo4jPropertyIndex> propertyIndexes;

    private Neo4jGraphSchema(GraphNodeTypeRegistry nodeTypes) {
        this.nodeTypes = nodeTypes;
        this.identityConstraints = buildIdentityConstraints(nodeTypes);
        this.propertyIndexes = buildPropertyIndexes();
    }

    public static Neo4jGraphSchema defaults() {
        return new Neo4jGraphSchema(GraphNodeTypeRegistry.defaults());
    }

    public GraphNodeTypeRegistry nodeTypes() {
        return nodeTypes;
    }

    public List<Neo4jIdentityConstraint> identityConstraints() {
        return identityConstraints;
    }

    public List<Neo4jPropertyIndex> propertyIndexes() {
        return propertyIndexes;
    }

    public List<String> cypherStatements() {
        List<String> statements = new ArrayList<>();
        for (Neo4jIdentityConstraint constraint : identityConstraints) {
            statements.add(constraint.cypher());
        }
        for (Neo4jPropertyIndex index : propertyIndexes) {
            statements.add(index.cypher());
        }
        return List.copyOf(statements);
    }

    public String mergeNodeCypher(String label) {
        GraphNodeType nodeType = nodeTypes.require(label);
        String property = nodeType.identityProperty();
        return "MERGE (n:CodeAtlasNode:" + label + " {" + property + ": $" + property + "}) "
                + "SET n.projectId = $projectId, n.snapshotId = $snapshotId, n.kind = $kind";
    }

    public String findNodeCypher(String label) {
        GraphNodeType nodeType = nodeTypes.require(label);
        String property = nodeType.identityProperty();
        return "MATCH (n:CodeAtlasNode:" + label + " {" + property + ": $" + property + "}) "
                + "WHERE n.projectId = $projectId AND n.snapshotId = $snapshotId RETURN n";
    }

    private static List<Neo4jIdentityConstraint> buildIdentityConstraints(GraphNodeTypeRegistry registry) {
        List<Neo4jIdentityConstraint> constraints = new ArrayList<>();
        for (GraphNodeType nodeType : registry.all()) {
            constraints.add(new Neo4jIdentityConstraint(
                    "codeatlas_" + toSnakeCase(nodeType.label()) + "_" + toSnakeCase(nodeType.identityProperty()),
                    nodeType.label(),
                    nodeType.identityProperty()));
        }
        return List.copyOf(constraints);
    }

    private static List<Neo4jPropertyIndex> buildPropertyIndexes() {
        return List.of(
                new Neo4jPropertyIndex(
                        "codeatlas_node_project_snapshot",
                        "(n:CodeAtlasNode)",
                        "n",
                        List.of("projectId", "snapshotId")),
                new Neo4jPropertyIndex(
                        "codeatlas_node_kind",
                        "(n:CodeAtlasNode)",
                        "n",
                        List.of("kind")),
                new Neo4jPropertyIndex(
                        "codeatlas_fact_active_scope",
                        "()-[r:CODEATLAS_FACT]-()",
                        "r",
                        List.of("projectId", "snapshotId", "relationType", "active")));
    }

    private static String toSnakeCase(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                builder.append('_');
            }
            builder.append(Character.toLowerCase(c));
        }
        return builder.toString();
    }
}
