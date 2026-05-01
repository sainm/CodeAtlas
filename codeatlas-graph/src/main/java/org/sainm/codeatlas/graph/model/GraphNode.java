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
        mergeCodeOriginProperties(mergedProperties, properties, other.properties);
        return new GraphNode(symbolId, mergedRoles, mergedProperties);
    }

    private static void mergeCodeOriginProperties(
        TreeMap<String, String> mergedProperties,
        Map<String, String> left,
        Map<String, String> right
    ) {
        boolean hasSource = flag(left, "hasSource") || flag(right, "hasSource");
        boolean hasJvm = flag(left, "hasJvm") || flag(right, "hasJvm");
        if (!hasSource && !hasJvm) {
            return;
        }
        mergedProperties.put("hasSource", Boolean.toString(hasSource));
        mergedProperties.put("hasJvm", Boolean.toString(hasJvm));
        mergedProperties.put("sourceOnly", Boolean.toString(hasSource && !hasJvm));
        mergedProperties.put("jvmOnly", Boolean.toString(hasJvm && !hasSource));
        mergedProperties.put("codeOrigin", hasSource && hasJvm ? "source+jvm" : hasSource ? "source" : "jvm");
    }

    private static boolean flag(Map<String, String> properties, String key) {
        return Boolean.parseBoolean(properties.getOrDefault(key, "false"));
    }
}
