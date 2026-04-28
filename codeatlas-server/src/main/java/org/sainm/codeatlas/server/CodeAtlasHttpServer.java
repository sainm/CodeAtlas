package org.sainm.codeatlas.server;

import org.sainm.codeatlas.graph.impact.ImpactReportJsonExporter;
import org.sainm.codeatlas.graph.impact.FastImpactAnalyzer;
import org.sainm.codeatlas.graph.neo4j.CypherStatement;
import org.sainm.codeatlas.graph.neo4j.Neo4jGraphQueryBuilder;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.model.SymbolKind;
import org.sainm.codeatlas.graph.search.SymbolSearchIndex;
import org.sainm.codeatlas.graph.search.SymbolSearchResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public final class CodeAtlasHttpServer {
    private final HttpServer server;
    private final ReportStore reportStore;
    private final ActiveFactStore activeFactStore;
    private final SymbolSearchIndex symbolSearchIndex;
    private final ImpactReportJsonExporter jsonExporter = new ImpactReportJsonExporter();
    private final Neo4jGraphQueryBuilder graphQueryBuilder = new Neo4jGraphQueryBuilder();

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
        server.createContext("/health", this::health);
        server.createContext("/api/symbols/search", this::symbolSearch);
        server.createContext("/api/reports", this::reports);
        server.createContext("/api/impact/analyze", this::impactAnalyze);
        server.createContext("/api/graph/callers", this::graphCallers);
        server.createContext("/api/graph/callees", this::graphCallees);
        server.createContext("/api/graph/impact-paths/query", this::graphImpactPathsQuery);
        server.createContext("/api/variables/trace-source", this::variableTraceSource);
        server.createContext("/api/variables/trace-sink", this::variableTraceSink);
        server.createContext("/api/jsp/backend-flow/query", this::jspBackendFlowQuery);
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
            symbol -> symbol.kind() == SymbolKind.API_ENDPOINT || symbol.kind() == SymbolKind.ACTION_PATH || symbol.value().contains("Controller") || symbol.value().contains("Action"),
            intValue(query.get("maxDepth"), 6),
            intValue(query.get("limit"), 50)
        );
        reportStore.putReport(report);
        send(exchange, 200, jsonExporter.export(report));
    }

    private SymbolId symbolFromValue(String value) {
        if (value.startsWith("method://")) {
            String body = value.substring("method://".length());
            String[] slash = body.split("/", 4);
            String sourceRootAndRest = slash.length > 3 ? slash[3] : "_";
            int memberIndex = sourceRootAndRest.indexOf('#');
            String owner = memberIndex >= 0 ? sourceRootAndRest.substring(0, memberIndex) : sourceRootAndRest;
            String memberAndDesc = memberIndex >= 0 ? sourceRootAndRest.substring(memberIndex + 1) : "_";
            int descriptorIndex = memberAndDesc.indexOf('(');
            String member = descriptorIndex >= 0 ? memberAndDesc.substring(0, descriptorIndex) : memberAndDesc;
            String descriptor = descriptorIndex >= 0 ? memberAndDesc.substring(descriptorIndex) : "_unknown";
            return SymbolId.method(slash[0], slash.length > 1 ? slash[1] : "_root", slash.length > 2 ? slash[2] : "_", owner, member, descriptor);
        }
        return SymbolId.logicalPath(SymbolKind.CONFIG_KEY, "unknown", "_root", "_", value, null);
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

    private String mapJson(Map<String, Object> map) {
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
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
