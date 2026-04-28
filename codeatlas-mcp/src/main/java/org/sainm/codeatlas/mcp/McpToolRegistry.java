package org.sainm.codeatlas.mcp;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class McpToolRegistry {
    private final Map<McpToolName, McpToolDescriptor> tools;

    public McpToolRegistry(List<McpToolDescriptor> descriptors) {
        tools = descriptors.stream()
            .collect(Collectors.toUnmodifiableMap(McpToolDescriptor::name, Function.identity()));
    }

    public static McpToolRegistry defaultReadOnlyRegistry() {
        return new McpToolRegistry(Arrays.stream(McpToolName.values())
            .map(name -> new McpToolDescriptor(name, description(name), true, timeout(name), inputSchema(name)))
            .toList());
    }

    public List<McpToolDescriptor> listTools() {
        return tools.values().stream()
            .sorted(java.util.Comparator.comparing(tool -> tool.name().value()))
            .toList();
    }

    public Optional<McpToolDescriptor> find(McpToolName name) {
        return Optional.ofNullable(tools.get(name));
    }

    public boolean isAllowed(McpToolName name) {
        return tools.containsKey(name) && tools.get(name).readOnly();
    }

    private static String description(McpToolName name) {
        return switch (name) {
            case SYMBOL_SEARCH -> "Search symbols by exact or fuzzy identifier.";
            case GRAPH_FIND_CALLERS -> "Find upstream callers for a symbol.";
            case GRAPH_FIND_CALLEES -> "Find downstream callees for a symbol.";
            case GRAPH_FIND_IMPACT_PATHS -> "Find impact paths from changed symbols.";
            case VARIABLE_TRACE_SOURCE -> "Trace where a variable value comes from.";
            case VARIABLE_TRACE_SINK -> "Trace where a variable value flows to.";
            case JSP_FIND_BACKEND_FLOW -> "Find JSP to backend action/controller flow.";
            case IMPACT_ANALYZE_DIFF -> "Analyze impact from a source diff.";
            case RAG_SEMANTIC_SEARCH -> "Search summaries and evidence packs semantically.";
            case REPORT_GET_IMPACT_REPORT -> "Read a generated impact report.";
        };
    }

    private static int timeout(McpToolName name) {
        return switch (name) {
            case IMPACT_ANALYZE_DIFF, GRAPH_FIND_IMPACT_PATHS -> 60;
            default -> 30;
        };
    }

    private static String inputSchema(McpToolName name) {
        return switch (name) {
            case SYMBOL_SEARCH -> """
                {"type":"object","required":["q"],"properties":{"q":{"type":"string"},"limit":{"type":"integer","default":20}}}
                """;
            case GRAPH_FIND_CALLERS, GRAPH_FIND_CALLEES -> """
                {"type":"object","required":["projectId","snapshotId","symbolId"],"properties":{"projectId":{"type":"string"},"snapshotId":{"type":"string"},"symbolId":{"type":"string"},"limit":{"type":"integer","default":50}}}
                """;
            case GRAPH_FIND_IMPACT_PATHS -> """
                {"type":"object","required":["projectId","snapshotId","symbolId"],"properties":{"projectId":{"type":"string"},"snapshotId":{"type":"string"},"symbolId":{"type":"string"},"maxDepth":{"type":"integer","default":6},"limit":{"type":"integer","default":50}}}
                """;
            case VARIABLE_TRACE_SOURCE, VARIABLE_TRACE_SINK -> """
                {"type":"object","required":["projectId","snapshotId","symbolId"],"properties":{"projectId":{"type":"string"},"snapshotId":{"type":"string"},"symbolId":{"type":"string","description":"REQUEST_PARAMETER symbolId"},"limit":{"type":"integer","default":50}}}
                """;
            case JSP_FIND_BACKEND_FLOW -> """
                {"type":"object","required":["projectId","snapshotId","symbolId"],"properties":{"projectId":{"type":"string"},"snapshotId":{"type":"string"},"symbolId":{"type":"string","description":"JSP_PAGE or JSP_FORM symbolId"},"maxDepth":{"type":"integer","default":8},"limit":{"type":"integer","default":50}}}
                """;
            case IMPACT_ANALYZE_DIFF -> """
                {"type":"object","required":["projectId","snapshotId","diffText"],"properties":{"projectId":{"type":"string"},"snapshotId":{"type":"string"},"diffText":{"type":"string"},"maxDepth":{"type":"integer","default":6},"limit":{"type":"integer","default":50}}}
                """;
            case RAG_SEMANTIC_SEARCH -> """
                {"type":"object","required":["projectId","snapshotId","q"],"properties":{"projectId":{"type":"string"},"snapshotId":{"type":"string"},"q":{"type":"string"},"limit":{"type":"integer","default":20}}}
                """;
            case REPORT_GET_IMPACT_REPORT -> """
                {"type":"object","required":["reportId"],"properties":{"reportId":{"type":"string"}}}
                """;
            default -> "{}";
        };
    }
}
