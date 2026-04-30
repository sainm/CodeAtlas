package org.sainm.codeatlas.ai.agent;

import java.util.Arrays;
import java.util.Optional;

public enum AgentToolName {
    QUERY_PLAN("query.plan"),
    SYMBOL_SEARCH("symbol.search"),
    GRAPH_FIND_CALLERS("graph.findCallers"),
    GRAPH_FIND_CALLEES("graph.findCallees"),
    GRAPH_FIND_IMPACT_PATHS("graph.findImpactPaths"),
    VARIABLE_TRACE("variable.trace"),
    VARIABLE_TRACE_SOURCE("variable.traceSource"),
    VARIABLE_TRACE_SINK("variable.traceSink"),
    JSP_FIND_BACKEND_FLOW("jsp.findBackendFlow"),
    IMPACT_ANALYZE_DIFF("impact.analyzeDiff"),
    RAG_SEMANTIC_SEARCH("rag.semanticSearch"),
    REPORT_GET_IMPACT_REPORT("report.getImpactReport");

    private final String value;

    AgentToolName(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Optional<AgentToolName> fromValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
            .filter(name -> name.value.equals(value.trim()))
            .findFirst();
    }
}
