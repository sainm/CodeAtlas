package org.sainm.codeatlas.ai.agent;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class AgentToolRegistry {
    private final Map<AgentToolName, AgentToolDescriptor> tools;

    public AgentToolRegistry(List<AgentToolDescriptor> descriptors) {
        tools = descriptors.stream()
            .collect(Collectors.toUnmodifiableMap(AgentToolDescriptor::name, Function.identity()));
    }

    public static AgentToolRegistry defaultReadOnlyRegistry() {
        return new AgentToolRegistry(Arrays.stream(AgentToolName.values())
            .map(name -> new AgentToolDescriptor(name, description(name), true, timeout(name)))
            .toList());
    }

    public List<AgentToolDescriptor> listTools() {
        return tools.values().stream()
            .sorted(java.util.Comparator.comparing(tool -> tool.name().value()))
            .toList();
    }

    public Optional<AgentToolDescriptor> find(AgentToolName name) {
        return Optional.ofNullable(tools.get(name));
    }

    public boolean isAllowed(AgentToolName name) {
        return tools.containsKey(name) && tools.get(name).readOnly();
    }

    private static String description(AgentToolName name) {
        return switch (name) {
            case QUERY_PLAN -> "Plan a safe read-only query from natural language.";
            case SYMBOL_SEARCH -> "Search exact and fuzzy project symbols.";
            case GRAPH_FIND_CALLERS -> "Read upstream caller facts for a symbol.";
            case GRAPH_FIND_CALLEES -> "Read downstream callee facts for a symbol.";
            case GRAPH_FIND_IMPACT_PATHS -> "Read bounded impact paths from graph facts.";
            case VARIABLE_TRACE -> "Read request-parameter source and sink facts together, including JSP page/form/input, argument propagation, and table paths.";
            case VARIABLE_TRACE_SOURCE -> "Read request-parameter source facts.";
            case VARIABLE_TRACE_SINK -> "Read request-parameter sink facts through argument propagation and table effects.";
            case JSP_FIND_BACKEND_FLOW -> "Read JSP, Struts, Java, and SQL backend-flow paths.";
            case IMPACT_ANALYZE_DIFF -> "Run fast read-only impact analysis from diff or changed symbols.";
            case RAG_SEMANTIC_SEARCH -> "Search exact symbols, semantic summaries, historical reports, and graph-neighbor evidence.";
            case RAG_ANSWER_DRAFT -> "Build a static evidence-backed answer draft from RAG search results.";
            case PROJECT_OVERVIEW -> "Read project capabilities, analysis status, and suggested analysis entrypoints.";
            case REPORT_GET_IMPACT_REPORT -> "Read generated impact reports.";
        };
    }

    private static int timeout(AgentToolName name) {
        return switch (name) {
            case IMPACT_ANALYZE_DIFF, GRAPH_FIND_IMPACT_PATHS -> 60;
            default -> 30;
        };
    }
}
