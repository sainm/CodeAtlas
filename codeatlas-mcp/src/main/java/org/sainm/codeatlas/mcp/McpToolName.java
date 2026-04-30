package org.sainm.codeatlas.mcp;

public enum McpToolName {
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
    REPORT_GET_IMPACT_REPORT("report.getImpactReport"),
    REPORT_GET_ASSISTANT_SUMMARY("report.getAssistantSummary"),
    QUERY_PLAN("query.plan");

    private final String value;

    McpToolName(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
