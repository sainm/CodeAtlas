package org.sainm.codeatlas.server;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class NaturalLanguageQueryPlanner {
    public QueryPlan plan(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isBlank()) {
            throw new IllegalArgumentException("q is required");
        }
        String normalized = query.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "variable", "trace", "parameter", "param", "source", "sink", "from where", "where from",
            "\u53d8\u91cf", "\u53c2\u6570", "\u8f93\u5165\u503c", "\u6765\u6e90", "\u6d41\u5411", "\u6d41\u5230",
            "\u4ece\u54ea\u91cc", "\u5230\u54ea\u91cc", "\u53bb\u54ea\u91cc", "\u53bb\u54ea", "\u53bb\u4e86\u54ea\u91cc", "\u54ea\u91cc\u6765")) {
            return variablePlan();
        }
        if (containsAny(normalized, "diff", "\u8865\u4e01")) {
            return diffImpactPlan();
        }
        if (containsAny(normalized, "impact", "change", "changed", "risk",
            "\u5f71\u54cd", "\u53d8\u66f4", "\u6539\u4e86", "\u98ce\u9669")) {
            return impactPlan();
        }
        if (containsAny(normalized, "caller", "callee", "call graph", "who calls", "calls whom",
            "\u88ab\u8c01\u8c03\u7528", "\u8c03\u7528\u8c01", "\u8c03\u7528\u56fe")) {
            return graphPlan();
        }
        if (containsAny(normalized, "sql", "table", "column", "mapper", "database", "db",
            "\u8868", "\u5b57\u6bb5", "\u6570\u636e\u5e93")) {
            return sqlPlan();
        }
        if (containsAny(normalized, "jsp", "page", "form", "tag", "struts", "action", "dispatchaction", "lookupdispatchaction",
            "\u9875\u9762", "\u8868\u5355", "\u94fe\u8def", "\u63d0\u4ea4", "\u8df3\u8f6c")) {
            return jspPlan();
        }
        return symbolSearchPlan();
    }

    private QueryPlan variablePlan() {
        return new QueryPlan(
            "VARIABLE_TRACE",
            "/api/variables/trace/report",
            "GET",
            "Trace request parameter sources and sinks through JSP pages, inputs, Struts actions, Java reads, argument propagation, and table effects.",
            List.of("projectId", "snapshotId", "symbolId"),
            Map.of("maxDepth", "4", "limit", "50"),
            List.of("DECLARES", "WRITES_PARAM", "READS_PARAM", "PASSES_PARAM", "BINDS_TO", "COVERED_BY", "READS_TABLE", "WRITES_TABLE"),
            "VARIABLE_TRACE_VIEW"
        );
    }

    private QueryPlan jspPlan() {
        return new QueryPlan(
            "JSP_BACKEND_FLOW",
            "/api/jsp/backend-flow/report",
            "GET",
            "Find backend paths from a JSP page through includes, forms, Struts routes, Java calls, SQL, and table facts.",
            List.of("projectId", "snapshotId", "symbolId"),
            Map.of("maxDepth", "8", "limit", "50"),
            List.of("INCLUDES", "FORWARDS_TO", "SUBMITS_TO", "WRITES_PARAM", "READS_PARAM", "ROUTES_TO", "CALLS", "BINDS_TO", "READS_TABLE", "WRITES_TABLE"),
            "JSP_FLOW_VIEW"
        );
    }

    private QueryPlan sqlPlan() {
        return new QueryPlan(
            "SQL_TABLE_IMPACT",
            "/api/graph/impact-paths/query",
            "GET",
            "Start from a SQL statement, table, or column symbol and expand active graph paths to affected entrypoints.",
            List.of("projectId", "snapshotId", "symbolId"),
            Map.of("maxDepth", "6", "limit", "50"),
            List.of("BINDS_TO", "CALLS", "ROUTES_TO", "SUBMITS_TO", "READS_TABLE", "WRITES_TABLE"),
            "SQL_TABLE_VIEW"
        );
    }

    private QueryPlan graphPlan() {
        return new QueryPlan(
            "CALL_GRAPH",
            "/api/graph/callers/report or /api/graph/callees/report",
            "GET",
            "Resolve callers or callees for an exact method symbol with active facts, confidence, and evidence keys.",
            List.of("projectId", "snapshotId", "symbolId"),
            Map.of("maxDepth", "2", "limit", "50"),
            List.of("CALLS", "IMPLEMENTS", "BRIDGES_TO"),
            "GRAPH_NEIGHBOR_VIEW"
        );
    }

    private QueryPlan impactPlan() {
        return new QueryPlan(
            "IMPACT_ANALYSIS",
            "/api/impact/analyze",
            "GET",
            "Run fast impact analysis from a changed symbol using active graph facts and evidence-carrying paths.",
            List.of("projectId", "snapshotId", "changedSymbol"),
            Map.of("maxDepth", "6", "limit", "50", "changeSetId", "manual"),
            List.of("CALLS", "ROUTES_TO", "SUBMITS_TO", "INCLUDES", "BINDS_TO", "FORWARDS_TO", "READS_TABLE", "WRITES_TABLE"),
            "IMPACT_REPORT_VIEW"
        );
    }

    private QueryPlan diffImpactPlan() {
        return new QueryPlan(
            "DIFF_IMPACT_ANALYSIS",
            "/api/impact/analyze-diff",
            "GET",
            "Run fast impact analysis from unified diff text by mapping changed files to active evidence symbols.",
            List.of("projectId", "snapshotId", "diffText"),
            Map.of("maxDepth", "6", "limit", "50", "changeSetId", "diff"),
            List.of("CALLS", "ROUTES_TO", "SUBMITS_TO", "INCLUDES", "BINDS_TO", "FORWARDS_TO", "READS_TABLE", "WRITES_TABLE"),
            "IMPACT_REPORT_VIEW"
        );
    }

    private QueryPlan symbolSearchPlan() {
        return new QueryPlan(
            "SYMBOL_SEARCH",
            "/api/symbols/search",
            "GET",
            "Search exact and fuzzy symbols first, then let the user choose graph, variable, JSP, SQL, or impact follow-up.",
            List.of("q"),
            Map.of("limit", "20"),
            List.of(),
            "SYMBOL_PICKER_VIEW"
        );
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
