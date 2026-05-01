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
            case GRAPH_FIND_CALLERS -> "Find upstream callers for a symbol with active facts, confidence, and evidence keys.";
            case GRAPH_FIND_CALLEES -> "Find downstream callees for a symbol with active facts, confidence, and evidence keys.";
            case GRAPH_FIND_IMPACT_PATHS -> "Find impact paths from changed symbols.";
            case VARIABLE_TRACE -> "Trace variable sources and sinks together with JSP page/form/input, argument propagation, and table evidence paths.";
            case VARIABLE_TRACE_SOURCE -> "Trace where a variable value comes from with evidence paths.";
            case VARIABLE_TRACE_SINK -> "Trace where a variable value flows to through argument propagation and table effects with evidence paths.";
            case JSP_FIND_BACKEND_FLOW -> "Find JSP to backend action/controller flow.";
            case IMPACT_ANALYZE_DIFF -> "Analyze impact from unified diff text by mapping changed files to active evidence symbols.";
            case RAG_SEMANTIC_SEARCH -> "Search exact symbols, semantic summaries, historical reports, and graph-neighbor evidence.";
            case RAG_ANSWER_DRAFT -> "Build a static answer draft from RAG search results with evidence keys.";
            case REPORT_GET_IMPACT_REPORT -> "Read a generated impact report.";
            case REPORT_GET_ASSISTANT_SUMMARY -> "Read report summary, risk explanation, test suggestions, evidence count, and AI/static fallback state.";
            case PROJECT_OVERVIEW -> "Read project capabilities, analysis status, and suggested entrypoints for dashboard or agent orientation.";
            case QUERY_PLAN -> "Plan a safe read-only query from a natural-language question.";
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
                {"type":"object","required":["projectId","snapshotId","symbolId"],"properties":{"projectId":{"type":"string"},"snapshotId":{"type":"string"},"symbolId":{"type":"string"},"maxDepth":{"type":"integer","default":2},"limit":{"type":"integer","default":50}}}
                """;
            case GRAPH_FIND_IMPACT_PATHS -> """
                {"type":"object","required":["projectId","snapshotId","symbolId"],"properties":{"projectId":{"type":"string"},"snapshotId":{"type":"string"},"symbolId":{"type":"string"},"maxDepth":{"type":"integer","default":6},"limit":{"type":"integer","default":50}}}
                """;
            case VARIABLE_TRACE, VARIABLE_TRACE_SOURCE, VARIABLE_TRACE_SINK -> """
                {"type":"object","required":["projectId","snapshotId","symbolId"],"properties":{"projectId":{"type":"string"},"snapshotId":{"type":"string"},"symbolId":{"type":"string","description":"REQUEST_PARAMETER symbolId"},"maxDepth":{"type":"integer","default":4},"limit":{"type":"integer","default":50}}}
                """;
            case JSP_FIND_BACKEND_FLOW -> """
                {"type":"object","required":["projectId","snapshotId","symbolId"],"properties":{"projectId":{"type":"string"},"snapshotId":{"type":"string"},"symbolId":{"type":"string","description":"JSP_PAGE or JSP_FORM symbolId"},"maxDepth":{"type":"integer","default":8},"limit":{"type":"integer","default":50}}}
                """;
            case IMPACT_ANALYZE_DIFF -> """
                {"type":"object","required":["projectId","snapshotId","diffText"],"properties":{"projectId":{"type":"string"},"snapshotId":{"type":"string"},"diffText":{"type":"string","description":"Unified diff text"},"changeSetId":{"type":"string","default":"diff"},"maxDepth":{"type":"integer","default":6},"limit":{"type":"integer","default":50}}}
                """;
            case RAG_SEMANTIC_SEARCH, RAG_ANSWER_DRAFT -> """
                {"type":"object","required":["projectId","snapshotId","q"],"properties":{"projectId":{"type":"string"},"snapshotId":{"type":"string"},"q":{"type":"string"},"limit":{"type":"integer","default":20}}}
                """;
            case REPORT_GET_IMPACT_REPORT, REPORT_GET_ASSISTANT_SUMMARY -> """
                {"type":"object","required":["reportId"],"properties":{"reportId":{"type":"string"}}}
                """;
            case PROJECT_OVERVIEW -> """
                {"type":"object","required":["projectId","snapshotId"],"properties":{"projectId":{"type":"string"},"snapshotId":{"type":"string"}}}
                """;
            case QUERY_PLAN -> """
                {"type":"object","required":["q"],"properties":{"q":{"type":"string","description":"Natural-language analysis question"}}}
                """;
            default -> "{}";
        };
    }
}
