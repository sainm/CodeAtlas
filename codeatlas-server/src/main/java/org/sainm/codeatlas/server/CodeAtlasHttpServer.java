package org.sainm.codeatlas.server;

import org.sainm.codeatlas.analyzers.git.ChangedFile;
import org.sainm.codeatlas.analyzers.git.UnifiedDiffParser;
import org.sainm.codeatlas.ai.AiProjectPolicy;
import org.sainm.codeatlas.ai.AiReportAssistant;
import org.sainm.codeatlas.ai.AiRuntimeConfig;
import org.sainm.codeatlas.ai.AiTextResult;
import org.sainm.codeatlas.ai.ImpactAssistantResult;
import org.sainm.codeatlas.ai.ImpactPromptBuilder;
import org.sainm.codeatlas.ai.SourceRedactor;
import org.sainm.codeatlas.graph.impact.ImpactReportJsonExporter;
import org.sainm.codeatlas.graph.impact.FastImpactAnalyzer;
import org.sainm.codeatlas.graph.flow.GraphFlowJsonExporter;
import org.sainm.codeatlas.graph.flow.GraphFlowQueryEngine;
import org.sainm.codeatlas.graph.neo4j.CypherStatement;
import org.sainm.codeatlas.graph.neo4j.Neo4jGraphQueryBuilder;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.model.SymbolKind;
import org.sainm.codeatlas.graph.search.SymbolSearchIndex;
import org.sainm.codeatlas.graph.search.SymbolSearchResult;
import org.sainm.codeatlas.graph.store.ActiveFact;
import org.sainm.codeatlas.graph.variable.VariableTraceJsonExporter;
import org.sainm.codeatlas.graph.variable.VariableTraceQueryEngine;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class CodeAtlasHttpServer {
    private final HttpServer server;
    private final ReportStore reportStore;
    private final ActiveFactStore activeFactStore;
    private final SymbolSearchIndex symbolSearchIndex;
    private final ImpactReportJsonExporter jsonExporter = new ImpactReportJsonExporter();
    private final Neo4jGraphQueryBuilder graphQueryBuilder = new Neo4jGraphQueryBuilder();
    private final NaturalLanguageQueryPlanner queryPlanner = new NaturalLanguageQueryPlanner();
    private final BusinessQueryResolver businessQueryResolver = new BusinessQueryResolver();
    private final SymbolIdValueParser symbolIdValueParser = new SymbolIdValueParser();
    private final ImpactEntrypointPredicate entrypointPredicate = new ImpactEntrypointPredicate();
    private final QueryResultViewRegistry resultViewRegistry = new QueryResultViewRegistry();
    private final VariableTraceQueryEngine variableTraceQueryEngine = new VariableTraceQueryEngine();
    private final VariableTraceJsonExporter variableTraceJsonExporter = new VariableTraceJsonExporter();
    private final GraphFlowQueryEngine graphFlowQueryEngine = new GraphFlowQueryEngine();
    private final GraphFlowJsonExporter graphFlowJsonExporter = new GraphFlowJsonExporter();
    private final UnifiedDiffParser unifiedDiffParser = new UnifiedDiffParser();
    private final AiReportAssistant aiReportAssistant = new AiReportAssistant(
        (prompt, config) -> AiTextResult.failure("AI provider is not configured for the local server"),
        new ImpactPromptBuilder(new SourceRedactor())
    );

    public CodeAtlasHttpServer(InetSocketAddress address, ReportStore reportStore) throws IOException {
        this(address, reportStore, new SymbolSearchIndex(), (projectId, snapshotId) -> java.util.List.of());
    }

    public CodeAtlasHttpServer(InetSocketAddress address, ReportStore reportStore, SymbolSearchIndex symbolSearchIndex) throws IOException {
        this(address, reportStore, symbolSearchIndex, (projectId, snapshotId) -> java.util.List.of());
    }

    public CodeAtlasHttpServer(
        InetSocketAddress address,
        ReportStore reportStore,
        SymbolSearchIndex symbolSearchIndex,
        ActiveFactStore activeFactStore
    ) throws IOException {
        this.server = HttpServer.create(address, 0);
        this.reportStore = reportStore;
        this.symbolSearchIndex = symbolSearchIndex;
        this.activeFactStore = activeFactStore;
        server.createContext("/health", exchange -> safely(exchange, this::health));
        server.createContext("/api/symbols/search", exchange -> safely(exchange, this::symbolSearch));
        server.createContext("/api/reports", exchange -> safely(exchange, this::reports));
        server.createContext("/api/reports-assistant", exchange -> safely(exchange, this::reportAssistant));
        server.createContext("/api/query/plan", exchange -> safely(exchange, this::queryPlan));
        server.createContext("/api/query/resolve", exchange -> safely(exchange, this::queryResolve));
        server.createContext("/api/query/views", exchange -> safely(exchange, this::queryViews));
        server.createContext("/api/impact/analyze", exchange -> safely(exchange, this::impactAnalyze));
        server.createContext("/api/impact/analyze-diff", exchange -> safely(exchange, this::impactAnalyzeDiff));
        server.createContext("/api/graph/callers", exchange -> safely(exchange, this::graphCallers));
        server.createContext("/api/graph/callees", exchange -> safely(exchange, this::graphCallees));
        server.createContext("/api/graph/callers/report", exchange -> safely(exchange, this::graphCallersReport));
        server.createContext("/api/graph/callees/report", exchange -> safely(exchange, this::graphCalleesReport));
        server.createContext("/api/graph/impact-paths/query", exchange -> safely(exchange, this::graphImpactPathsQuery));
        server.createContext("/api/variables/trace-source", exchange -> safely(exchange, this::variableTraceSource));
        server.createContext("/api/variables/trace-sink", exchange -> safely(exchange, this::variableTraceSink));
        server.createContext("/api/variables/trace/report", exchange -> safely(exchange, this::variableTraceReport));
        server.createContext("/api/variables/trace-source/report", exchange -> safely(exchange, this::variableTraceSourceReport));
        server.createContext("/api/variables/trace-sink/report", exchange -> safely(exchange, this::variableTraceSinkReport));
        server.createContext("/api/jsp/backend-flow/query", exchange -> safely(exchange, this::jspBackendFlowQuery));
        server.createContext("/api/jsp/backend-flow/report", exchange -> safely(exchange, this::jspBackendFlowReport));
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void health(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }
        send(exchange, 200, "{\"status\":\"ok\"}");
    }

    private void reports(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String prefix = "/api/reports/";
        if (!path.startsWith(prefix) || path.length() == prefix.length()) {
            send(exchange, 404, "{\"error\":\"report_not_found\"}");
            return;
        }
        String reportId = path.substring(prefix.length());
        reportStore.findReport(reportId)
            .ifPresentOrElse(
                report -> sendUnchecked(exchange, 200, jsonExporter.export(report)),
                () -> sendUnchecked(exchange, 404, "{\"error\":\"report_not_found\"}")
            );
    }

    private void reportAssistant(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }
        Map<String, String> query = query(exchange);
        String reportId = required(query, "reportId");
        var report = reportStore.findReport(reportId)
            .orElseThrow(() -> new IllegalArgumentException("report not found: " + reportId));
        ImpactAssistantResult result = aiReportAssistant.analyzeImpact(
            report,
            AiRuntimeConfig.disabled(),
            AiProjectPolicy.disabled()
        );
        send(exchange, 200, assistantResultJson(reportId, result));
    }

    private void symbolSearch(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }
        Map<String, String> query = query(exchange);
        String q = required(query, "q");
        int limit = intValue(query.get("limit"), 20);
        String results = symbolSearchIndex.search(q, limit).stream()
            .map(this::symbolResultJson)
            .collect(Collectors.joining(",", "[", "]"));
        send(exchange, 200, "{\"results\":" + results + "}");
    }

    private void queryPlan(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }
        Map<String, String> query = query(exchange);
        QueryPlan plan = queryPlanner.plan(required(query, "q"));
        send(exchange, 200, queryPlanJson(plan));
    }

    private void queryResolve(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }
        Map<String, String> query = query(exchange);
        String q = required(query, "q");
        String projectId = query.getOrDefault("projectId", "shop");
        String snapshotId = query.getOrDefault("snapshotId", "s1");
        String moduleKey = query.getOrDefault("moduleKey", "_root");
        int limit = intValue(query.get("limit"), 10);
        QueryPlan plan = queryPlanner.plan(q);
        List<BusinessQueryCandidate> candidates = businessQueryResolver.resolve(
            q,
            plan,
            projectId,
            moduleKey,
            snapshotId,
            symbolSearchIndex,
            activeFactStore.activeFacts(projectId, snapshotId),
            limit
        );
        send(exchange, 200, queryResolveJson(plan, candidates));
    }

    private void queryViews(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }
        Map<String, String> query = query(exchange);
        java.util.List<QueryResultViewDescriptor> descriptors = query.containsKey("name")
            ? resultViewRegistry.find(query.get("name")).stream().toList()
            : resultViewRegistry.listViews();
        String views = descriptors.stream()
            .map(this::resultViewJson)
            .collect(Collectors.joining(",", "[", "]"));
        send(exchange, 200, "{\"views\":" + views + "}");
    }

    private void graphCallers(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }
        Map<String, String> query = query(exchange);
        CypherStatement statement = graphQueryBuilder.findCallers(
            required(query, "projectId"),
            required(query, "snapshotId"),
            required(query, "symbolId"),
            intValue(query.get("limit"), 50)
        );
        send(exchange, 200, statementJson(statement));
    }

    private void graphCallees(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }
        Map<String, String> query = query(exchange);
        CypherStatement statement = graphQueryBuilder.findCallees(
            required(query, "projectId"),
            required(query, "snapshotId"),
            required(query, "symbolId"),
            intValue(query.get("limit"), 50)
        );
        send(exchange, 200, statementJson(statement));
    }

    private void graphImpactPathsQuery(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }
        Map<String, String> query = query(exchange);
        CypherStatement statement = graphQueryBuilder.findImpactPaths(
            required(query, "projectId"),
            required(query, "snapshotId"),
            required(query, "symbolId"),
            intValue(query.get("maxDepth"), 6),
            intValue(query.get("limit"), 50)
        );
        send(exchange, 200, statementJson(statement));
    }

    private void graphCallersReport(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }
        Map<String, String> query = query(exchange);
        String projectId = required(query, "projectId");
        String snapshotId = required(query, "snapshotId");
        String symbolId = required(query, "symbolId");
        var paths = graphFlowQueryEngine.findUpstreamPaths(
            activeFactStore.activeFacts(projectId, snapshotId),
            symbolFromValue(symbolId),
            GraphFlowQueryEngine.CALL_GRAPH_RELATIONS,
            intValue(query.get("maxDepth"), 2),
            intValue(query.get("limit"), 50)
        );
        send(exchange, 200, graphFlowJsonExporter.export(projectId, snapshotId, symbolId, paths));
    }

    private void graphCalleesReport(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }
        Map<String, String> query = query(exchange);
        String projectId = required(query, "projectId");
        String snapshotId = required(query, "snapshotId");
        String symbolId = required(query, "symbolId");
        var paths = graphFlowQueryEngine.findDownstreamPaths(
            activeFactStore.activeFacts(projectId, snapshotId),
            symbolFromValue(symbolId),
            GraphFlowQueryEngine.CALL_GRAPH_RELATIONS,
            intValue(query.get("maxDepth"), 2),
            intValue(query.get("limit"), 50)
        );
        send(exchange, 200, graphFlowJsonExporter.export(projectId, snapshotId, symbolId, paths));
    }

    private void variableTraceSource(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }
        Map<String, String> query = query(exchange);
        CypherStatement statement = graphQueryBuilder.traceVariableSources(
            required(query, "projectId"),
            required(query, "snapshotId"),
            required(query, "symbolId"),
            intValue(query.get("limit"), 50)
        );
        send(exchange, 200, statementJson(statement));
    }

    private void variableTraceSink(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }
        Map<String, String> query = query(exchange);
        CypherStatement statement = graphQueryBuilder.traceVariableSinks(
            required(query, "projectId"),
            required(query, "snapshotId"),
            required(query, "symbolId"),
            intValue(query.get("limit"), 50)
        );
        send(exchange, 200, statementJson(statement));
    }

    private void variableTraceSourceReport(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }
        Map<String, String> query = query(exchange);
        String projectId = required(query, "projectId");
        String snapshotId = required(query, "snapshotId");
        String symbolId = required(query, "symbolId");
        var paths = variableTraceQueryEngine.findSourcePaths(
            activeFactStore.activeFacts(projectId, snapshotId),
            symbolFromValue(symbolId),
            intValue(query.get("maxDepth"), 4),
            intValue(query.get("limit"), 50)
        );
        send(exchange, 200, variableTraceJsonExporter.export(projectId, snapshotId, symbolId, paths));
    }

    private void variableTraceReport(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }
        Map<String, String> query = query(exchange);
        String projectId = required(query, "projectId");
        String snapshotId = required(query, "snapshotId");
        String symbolId = required(query, "symbolId");
        SymbolId parameter = symbolFromValue(symbolId);
        List<ActiveFact> activeFacts = activeFactStore.activeFacts(projectId, snapshotId);
        int maxDepth = intValue(query.get("maxDepth"), 4);
        int limit = intValue(query.get("limit"), 50);
        var sourcePaths = variableTraceQueryEngine.findSourcePaths(activeFacts, parameter, maxDepth, limit);
        var sinkPaths = variableTraceQueryEngine.findSinkPaths(activeFacts, parameter, maxDepth, limit);
        var paths = new java.util.ArrayList<>(sourcePaths);
        paths.addAll(sinkPaths);
        send(exchange, 200, variableTraceJsonExporter.export(projectId, snapshotId, symbolId, paths));
    }

    private void variableTraceSinkReport(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }
        Map<String, String> query = query(exchange);
        String projectId = required(query, "projectId");
        String snapshotId = required(query, "snapshotId");
        String symbolId = required(query, "symbolId");
        var paths = variableTraceQueryEngine.findSinkPaths(
            activeFactStore.activeFacts(projectId, snapshotId),
            symbolFromValue(symbolId),
            intValue(query.get("maxDepth"), 4),
            intValue(query.get("limit"), 50)
        );
        send(exchange, 200, variableTraceJsonExporter.export(projectId, snapshotId, symbolId, paths));
    }

    private void jspBackendFlowQuery(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }
        Map<String, String> query = query(exchange);
        CypherStatement statement = graphQueryBuilder.findJspBackendFlow(
            required(query, "projectId"),
            required(query, "snapshotId"),
            required(query, "symbolId"),
            intValue(query.get("maxDepth"), 8),
            intValue(query.get("limit"), 50)
        );
        send(exchange, 200, statementJson(statement));
    }

    private void jspBackendFlowReport(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }
        Map<String, String> query = query(exchange);
        String projectId = required(query, "projectId");
        String snapshotId = required(query, "snapshotId");
        String symbolId = required(query, "symbolId");
        var paths = graphFlowQueryEngine.findDownstreamPaths(
            activeFactStore.activeFacts(projectId, snapshotId),
            symbolFromValue(symbolId),
            GraphFlowQueryEngine.JSP_BACKEND_FLOW_RELATIONS,
            intValue(query.get("maxDepth"), 8),
            intValue(query.get("limit"), 50)
        );
        send(exchange, 200, graphFlowJsonExporter.export(projectId, snapshotId, symbolId, paths));
    }

    private void impactAnalyze(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }
        Map<String, String> query = query(exchange);
        String projectId = required(query, "projectId");
        String snapshotId = required(query, "snapshotId");
        String changedSymbol = required(query, "changedSymbol");
        String reportId = query.getOrDefault("reportId", "report-" + System.currentTimeMillis());
        var report = new FastImpactAnalyzer().analyze(
            reportId,
            projectId,
            snapshotId,
            query.getOrDefault("changeSetId", "manual"),
            activeFactStore.activeFacts(projectId, snapshotId),
            java.util.List.of(symbolFromValue(changedSymbol)),
            entrypointPredicate,
            intValue(query.get("maxDepth"), 6),
            intValue(query.get("limit"), 50)
        );
        reportStore.putReport(report);
        send(exchange, 200, jsonExporter.export(report));
    }

    private void impactAnalyzeDiff(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }
        Map<String, String> query = query(exchange);
        String projectId = required(query, "projectId");
        String snapshotId = required(query, "snapshotId");
        String diffText = required(query, "diffText");
        String reportId = query.getOrDefault("reportId", "report-" + System.currentTimeMillis());
        List<ActiveFact> activeFacts = activeFactStore.activeFacts(projectId, snapshotId);
        List<SymbolId> changedSymbols = changedSymbolsFromDiff(diffText, activeFacts);
        var report = new FastImpactAnalyzer().analyze(
            reportId,
            projectId,
            snapshotId,
            query.getOrDefault("changeSetId", "diff"),
            activeFacts,
            changedSymbols,
            entrypointPredicate,
            intValue(query.get("maxDepth"), 6),
            intValue(query.get("limit"), 50)
        );
        reportStore.putReport(report);
        send(exchange, 200, jsonExporter.export(report));
    }

    private List<SymbolId> changedSymbolsFromDiff(String diffText, List<ActiveFact> activeFacts) {
        Set<String> changedPaths = unifiedDiffParser.parseChangedFiles(diffText).stream()
            .map(ChangedFile::effectivePath)
            .map(this::normalizePath)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<SymbolId> symbols = new LinkedHashSet<>();
        for (ActiveFact activeFact : activeFacts) {
            boolean matched = activeFact.evidenceKeys().stream()
                .map(key -> normalizePath(key.path()))
                .anyMatch(path -> changedPaths.stream().anyMatch(changedPath -> pathMatches(changedPath, path)));
            if (matched) {
                symbols.add(activeFact.factKey().source());
                symbols.add(activeFact.factKey().target());
            }
        }
        return List.copyOf(symbols);
    }

    private boolean pathMatches(String changedPath, String evidencePath) {
        return evidencePath.equals(changedPath)
            || evidencePath.endsWith("/" + changedPath)
            || changedPath.endsWith("/" + evidencePath);
    }

    private String normalizePath(String path) {
        String normalized = path == null ? "" : path.trim().replace('\\', '/');
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private SymbolId symbolFromValue(String value) {
        return symbolIdValueParser.parse(value);
    }

    private Map<String, String> query(HttpExchange exchange) {
        String rawQuery = exchange.getRequestURI().getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) {
            return Map.of();
        }
        return Arrays.stream(rawQuery.split("&"))
            .filter(part -> !part.isBlank())
            .map(part -> part.split("=", 2))
            .collect(Collectors.toMap(
                part -> decode(part[0]),
                part -> part.length > 1 ? decode(part[1]) : "",
                (left, right) -> right
            ));
    }

    private String required(Map<String, String> query, String name) {
        String value = query.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private int intValue(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Integer.parseInt(value);
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String statementJson(CypherStatement statement) {
        return "{\"cypher\":\"" + escape(statement.cypher()) + "\",\"parameters\":" + mapJson(statement.parameters()) + "}";
    }

    private String symbolResultJson(SymbolSearchResult result) {
        return "{\"symbolId\":\"" + escape(result.symbolId().value())
            + "\",\"kind\":\"" + result.kind()
            + "\",\"displayName\":\"" + escape(result.displayName())
            + "\",\"score\":" + result.score()
            + "}";
    }

    private String queryPlanJson(QueryPlan plan) {
        return "{\"intent\":\"" + escape(plan.intent())
            + "\",\"endpoint\":\"" + escape(plan.endpoint())
            + "\",\"method\":\"" + escape(plan.method())
            + "\",\"summary\":\"" + escape(plan.summary())
            + "\",\"requiredParameters\":" + valueJson(plan.requiredParameters())
            + ",\"defaultParameters\":" + mapJson(plan.defaultParameters())
            + ",\"relationTypes\":" + valueJson(plan.relationTypes())
            + ",\"resultView\":\"" + escape(plan.resultView())
            + "\"}";
    }

    private String queryResolveJson(QueryPlan plan, List<BusinessQueryCandidate> candidates) {
        return "{\"plan\":" + queryPlanJson(plan)
            + ",\"candidates\":" + candidates.stream()
                .map(this::businessCandidateJson)
                .collect(Collectors.joining(",", "[", "]"))
            + "}";
    }

    private String businessCandidateJson(BusinessQueryCandidate candidate) {
        return "{\"symbolId\":\"" + escape(candidate.symbolId().value())
            + "\",\"kind\":\"" + candidate.kind()
            + "\",\"displayName\":\"" + escape(candidate.displayName())
            + "\",\"score\":" + candidate.score()
            + ",\"suggestedParameter\":\"" + escape(candidate.suggestedParameter())
            + "\",\"reason\":\"" + escape(candidate.reason())
            + "\"}";
    }

    private String resultViewJson(QueryResultViewDescriptor view) {
        return "{\"name\":\"" + escape(view.name())
            + "\",\"title\":\"" + escape(view.title())
            + "\",\"summary\":\"" + escape(view.summary())
            + "\",\"primaryFields\":" + valueJson(view.primaryFields())
            + ",\"evidenceFields\":" + valueJson(view.evidenceFields())
            + "}";
    }

    private String assistantResultJson(String reportId, ImpactAssistantResult result) {
        return "{\"reportId\":\"" + escape(reportId)
            + "\",\"summary\":\"" + escape(result.summary())
            + "\",\"riskExplanation\":\"" + escape(result.riskExplanation())
            + "\",\"testSuggestions\":" + valueJson(result.testSuggestions())
            + ",\"evidenceCount\":" + result.evidenceCount()
            + ",\"aiAssisted\":" + result.aiAssisted()
            + "}";
    }

    private String mapJson(Map<String, ?> map) {
        return map.entrySet().stream()
            .map(entry -> "\"" + escape(entry.getKey()) + "\":" + valueJson(entry.getValue()))
            .collect(Collectors.joining(",", "{", "}"));
    }

    private String valueJson(Object value) {
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Iterable<?> iterable) {
            String joined = java.util.stream.StreamSupport.stream(iterable.spliterator(), false)
                .map(this::valueJson)
                .collect(Collectors.joining(","));
            return "[" + joined + "]";
        }
        return "\"" + escape(value == null ? "" : value.toString()) + "\"";
    }

    private String escape(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private void sendUnchecked(HttpExchange exchange, int status, String body) {
        try {
            send(exchange, status, body);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to send response", exception);
        }
    }

    private void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        addCorsHeaders(exchange);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private void safely(HttpExchange exchange, ExchangeHandler handler) throws IOException {
        try {
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                sendNoContent(exchange, 204);
                return;
            }
            handler.handle(exchange);
        } catch (IllegalArgumentException exception) {
            send(exchange, 400, "{\"error\":\"bad_request\",\"message\":\"" + escape(exception.getMessage()) + "\"}");
        } catch (Exception exception) {
            send(exchange, 500, "{\"error\":\"internal_error\",\"message\":\"" + escape(exception.getMessage() == null ? "Unexpected server error" : exception.getMessage()) + "\"}");
        }
    }

    private void sendNoContent(HttpExchange exchange, int status) throws IOException {
        addCorsHeaders(exchange);
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    private void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws Exception;
    }
}
