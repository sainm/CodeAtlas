package org.sainm.codeatlas.graph.model;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public record GraphNode(
    SymbolId symbolId,
    Set<NodeRole> roles,
    Map<String, String> properties
) {
    public GraphNode {
        Objects.requireNonNull(symbolId, "symbolId");
        Objects.requireNonNull(roles, "roles");
        Objects.requireNonNull(properties, "properties");
        roles = Set.copyOf(new TreeSet<>(roles));
        properties = Map.copyOf(new TreeMap<>(properties));
    }

    public static GraphNode of(SymbolId symbolId, NodeRole role) {
        return new GraphNode(symbolId, Set.of(role), Map.of());
    }

    public GraphNode merge(GraphNode other) {
        if (!symbolId.equals(other.symbolId)) {
            throw new IllegalArgumentException("cannot merge different nodes");
        }
        TreeSet<NodeRole> mergedRoles = new TreeSet<>(roles);
        mergedRoles.addAll(other.roles);
        TreeMap<String, String> mergedProperties = new TreeMap<>(properties);
        mergedProperties.putAll(other.properties);
        return new GraphNode(symbolId, mergedRoles, mergedProperties);
    }
}

