package org.sainm.codeatlas.mcp;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class McpResourceRegistry {
    private final Map<McpResourceName, McpResourceDescriptor> resources;

    public McpResourceRegistry(List<McpResourceDescriptor> descriptors) {
        resources = descriptors.stream()
            .collect(Collectors.toUnmodifiableMap(McpResourceDescriptor::name, Function.identity()));
    }

    public static McpResourceRegistry defaultReadOnlyRegistry() {
        return new McpResourceRegistry(Arrays.stream(McpResourceName.values())
            .map(name -> new McpResourceDescriptor(name, uriTemplate(name), description(name), true))
            .toList());
    }

    public List<McpResourceDescriptor> listResources() {
        return resources.values().stream()
            .sorted(Comparator.comparing(resource -> resource.name().value()))
            .toList();
    }

    public Optional<McpResourceDescriptor> find(McpResourceName name) {
        return Optional.ofNullable(resources.get(name));
    }

    public boolean isAllowed(McpResourceName name) {
        return resources.containsKey(name) && resources.get(name).readOnly();
    }

    private static String uriTemplate(McpResourceName name) {
        return switch (name) {
            case SYMBOL -> "codeatlas://projects/{projectId}/snapshots/{snapshotId}/symbols/{symbolId}";
            case JSP -> "codeatlas://projects/{projectId}/snapshots/{snapshotId}/jsp/{symbolId}";
            case TABLE -> "codeatlas://projects/{projectId}/snapshots/{snapshotId}/tables/{symbolId}";
            case REPORT -> "codeatlas://reports/{reportId}";
        };
    }

    private static String description(McpResourceName name) {
        return switch (name) {
            case SYMBOL -> "Read symbol metadata, neighbors, confidence, and evidence references.";
            case JSP -> "Read JSP page/form/input facts and backend flow entrypoints.";
            case TABLE -> "Read database table impact facts and SQL statement references.";
            case REPORT -> "Read generated impact reports.";
        };
    }
}
