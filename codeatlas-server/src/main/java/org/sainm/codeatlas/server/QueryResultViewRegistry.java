package org.sainm.codeatlas.server;

import java.util.List;
import java.util.Optional;

public final class QueryResultViewRegistry {
    private final List<QueryResultViewDescriptor> views = List.of(
        new QueryResultViewDescriptor(
            "PROJECT_OVERVIEW_VIEW",
            "Project Overview",
            "Project-level status, supported artifact types, guided analysis entrypoints, and backend analysis state.",
            List.of("projectId", "snapshotId", "capabilities", "analysisStatus", "entrypoints"),
            List.of("sourceType", "confidence", "evidenceKeys", "path", "lineStart", "lineEnd")
        ),
        new QueryResultViewDescriptor(
            "IMPACT_REPORT_VIEW",
            "Impact Report",
            "Changed symbol, affected entrypoint, ordered path, confidence, risk, evidence, and truncation state.",
            List.of("reportId", "changedSymbol", "entrypoint", "riskLevel", "confidence", "truncated"),
            List.of("relationType", "sourceType", "confidence", "evidenceKey", "file", "line")
        ),
        new QueryResultViewDescriptor(
            "VARIABLE_TRACE_VIEW",
            "Variable Trace",
            "Request parameter sources and sinks, including JSP page/form/input sources, argument propagation, table effects, ActionForm bindings, Validator coverage, and evidence.",
            List.of("parameterSymbolId", "direction", "endpoint", "pathCount", "path", "confidence"),
            List.of("incomingRelation", "qualifier", "sourceType", "confidence", "evidenceKeys", "path", "lineStart", "lineEnd")
        ),
        new QueryResultViewDescriptor(
            "JSP_FLOW_VIEW",
            "JSP Flow",
            "JSP includes, form submission, Struts routing, Java calls, SQL/table links, and evidence path.",
            List.of("startSymbolId", "endpoint", "pathCount", "path", "confidence", "truncated"),
            List.of("incomingRelation", "qualifier", "sourceType", "confidence", "evidenceKeys", "path", "lineStart", "lineEnd")
        ),
        new QueryResultViewDescriptor(
            "GRAPH_NEIGHBOR_VIEW",
            "Graph Neighbors",
            "Caller/callee paths for exact symbols with active relation facts, confidence, source type, and evidence keys.",
            List.of("startSymbolId", "endpoint", "pathCount", "path", "confidence"),
            List.of("incomingRelation", "qualifier", "sourceType", "confidence", "evidenceKeys", "path", "lineStart", "lineEnd")
        ),
        new QueryResultViewDescriptor(
            "SQL_TABLE_VIEW",
            "SQL Table Impact",
            "SQL statement, table, column, mapper, and upstream web entrypoint impact paths.",
            List.of("sqlSymbolId", "tableSymbolId", "columnSymbolId", "entrypoint", "relationType"),
            List.of("sourceType", "confidence", "evidenceKey", "file", "line")
        ),
        new QueryResultViewDescriptor(
            "RAG_SEARCH_VIEW",
            "Semantic Code Search",
            "Exact symbol matches, vector summary recall, and nearby graph facts for natural-language code questions.",
            List.of("symbolId", "kind", "displayName", "summary", "score", "matchKinds"),
            List.of("matchKinds", "evidenceKeys", "sourceType", "confidence", "file", "line")
        ),
        new QueryResultViewDescriptor(
            "SYMBOL_PICKER_VIEW",
            "Symbol Picker",
            "Candidate symbols before running exact graph, variable, JSP, SQL, or impact queries.",
            List.of("symbolId", "kind", "displayName", "score"),
            List.of("file", "line", "moduleKey", "sourceRootKey")
        )
    );

    public List<QueryResultViewDescriptor> listViews() {
        return views;
    }

    public Optional<QueryResultViewDescriptor> find(String name) {
        return views.stream().filter(view -> view.name().equals(name)).findFirst();
    }
}
