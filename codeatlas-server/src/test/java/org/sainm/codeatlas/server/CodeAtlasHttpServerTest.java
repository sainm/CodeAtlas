package org.sainm.codeatlas.server;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.sainm.codeatlas.graph.impact.ImpactReport;
import org.sainm.codeatlas.graph.impact.ImpactPath;
import org.sainm.codeatlas.graph.impact.ImpactPathStep;
import org.sainm.codeatlas.graph.impact.ReportDepth;
import org.sainm.codeatlas.graph.impact.RiskLevel;
import org.sainm.codeatlas.graph.model.GraphNodeFactory;
import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.EvidenceKey;
import org.sainm.codeatlas.graph.model.FactKey;
import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.NodeRole;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.model.SymbolKind;
import org.sainm.codeatlas.graph.search.SymbolSearchIndex;
import org.sainm.codeatlas.graph.store.InMemoryGraphRepository;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class CodeAtlasHttpServerTest {
    @Test
    void servesHealthAndReportJson() throws Exception {
        InMemoryReportStore store = new InMemoryReportStore();
        store.putReport(new ImpactReport("r1", "shop", "s1", "c1", ReportDepth.FAST, null, List.of(), List.of(), false));
        CodeAtlasHttpServer server = new CodeAtlasHttpServer(new InetSocketAddress(0), store);
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://localhost:" + server.port();

            HttpResponse<String> health = client.send(
                HttpRequest.newBuilder(URI.create(base + "/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );
            HttpResponse<String> report = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/reports/r1")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );

            assertTrue(health.body().contains("\"ok\""));
            assertEquals("*", health.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
            assertTrue(health.headers().firstValue("Access-Control-Allow-Methods").orElse("").contains("GET"));
            assertTrue(report.body().contains("\"reportId\": \"r1\""));
        } finally {
            server.stop();
        }
    }

    @Test
    void servesReportAssistantFallbackSummary() throws Exception {
        SymbolId action = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserAction", "execute", "()V");
        SymbolId table = SymbolId.logicalPath(SymbolKind.DB_TABLE, "shop", "_root", "db", "users", null);
        ImpactPath path = ImpactPath.fromSteps(
            action,
            table,
            List.of(
                new ImpactPathStep(action, null, SourceType.SPOON, Confidence.CERTAIN),
                new ImpactPathStep(table, RelationType.WRITES_TABLE, SourceType.SQL, Confidence.LIKELY)
            ),
            RiskLevel.HIGH,
            "action writes table",
            false
        );
        InMemoryReportStore store = new InMemoryReportStore();
        store.putReport(new ImpactReport("r-ai", "shop", "s1", "c1", ReportDepth.FAST, null, List.of(path), List.of(), false));
        CodeAtlasHttpServer server = new CodeAtlasHttpServer(new InetSocketAddress(0), store);
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://localhost:" + server.port();

            HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/reports-assistant?reportId=r-ai")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );

            assertTrue(response.body().contains("\"reportId\":\"r-ai\""));
            assertTrue(response.body().contains("\"aiAssisted\":false"));
            assertTrue(response.body().contains("Highest static risk is HIGH"));
            assertTrue(response.body().contains("DAO/Mapper"));
        } finally {
            server.stop();
        }
    }

    @Test
    void servesGraphQueryPlanEndpoints() throws Exception {
        CodeAtlasHttpServer server = new CodeAtlasHttpServer(new InetSocketAddress(0), new InMemoryReportStore());
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://localhost:" + server.port();
            String symbolId = URLEncoder.encode("method://shop/_root/src/main/java/com.acme.UserService#save()V", StandardCharsets.UTF_8);

            HttpResponse<String> callers = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/graph/callers?projectId=shop&snapshotId=s1&symbolId=" + symbolId)).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );
            HttpResponse<String> impact = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/graph/impact-paths/query?projectId=shop&snapshotId=s1&symbolId=" + symbolId + "&maxDepth=3")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );

            assertTrue(callers.body().contains("\"cypher\""));
            assertTrue(callers.body().contains("findCallers") || callers.body().contains("MATCH"));
            assertTrue(impact.body().contains("[*1..3]"));
        } finally {
            server.stop();
        }
    }

    @Test
    void returnsBadRequestJsonForMissingQueryParameters() throws Exception {
        CodeAtlasHttpServer server = new CodeAtlasHttpServer(new InetSocketAddress(0), new InMemoryReportStore());
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://localhost:" + server.port();

            HttpResponse<String> queryPlan = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/query/plan")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );
            HttpResponse<String> callers = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/graph/callers?projectId=shop&snapshotId=s1")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(400, queryPlan.statusCode());
            assertEquals(400, callers.statusCode());
            assertTrue(queryPlan.body().contains("\"error\":\"bad_request\""));
            assertTrue(queryPlan.body().contains("q is required"));
            assertTrue(callers.body().contains("symbolId is required"));
        } finally {
            server.stop();
        }
    }

    @Test
    void servesCorsPreflightForApiEndpoints() throws Exception {
        CodeAtlasHttpServer server = new CodeAtlasHttpServer(new InetSocketAddress(0), new InMemoryReportStore());
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://localhost:" + server.port();

            HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/query/plan"))
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(204, response.statusCode());
            assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").orElse("").equals("*"));
            assertTrue(response.headers().firstValue("Access-Control-Allow-Methods").orElse("").contains("GET"));
        } finally {
            server.stop();
        }
    }

    @Test
    void servesVariableTraceQueryPlanEndpoints() throws Exception {
        CodeAtlasHttpServer server = new CodeAtlasHttpServer(new InetSocketAddress(0), new InMemoryReportStore());
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://localhost:" + server.port();
            String symbolId = URLEncoder.encode("request-parameter://shop/_root/_request/id", StandardCharsets.UTF_8);

            HttpResponse<String> source = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/variables/trace-source?projectId=shop&snapshotId=s1&symbolId=" + symbolId)).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );
            HttpResponse<String> sink = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/variables/trace-sink?projectId=shop&snapshotId=s1&symbolId=" + symbolId)).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );

            assertTrue(source.body().contains("WRITES_PARAM"));
            assertTrue(sink.body().contains("READS_PARAM"));
            assertTrue(sink.body().contains("BINDS_TO"));
            assertTrue(sink.body().contains("COVERED_BY"));
        } finally {
            server.stop();
        }
    }

    @Test
    void servesVariableTraceReportEndpointsFromActiveFacts() throws Exception {
        SymbolId parameter = SymbolId.logicalPath(SymbolKind.REQUEST_PARAMETER, "shop", "_root", "_request", "userId", null);
        SymbolId page = SymbolId.logicalPath(SymbolKind.JSP_PAGE, "shop", "_root", "src/main/webapp", "user/edit.jsp", null);
        SymbolId form = SymbolId.logicalPath(SymbolKind.JSP_FORM, "shop", "_root", "src/main/webapp", "user/edit.jsp", "form:0");
        SymbolId input = SymbolId.logicalPath(SymbolKind.JSP_INPUT, "shop", "_root", "src/main/webapp", "user/edit.jsp", "input:userId");
        SymbolId action = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserAction", "execute", "()V");
        InMemoryGraphRepository repository = new InMemoryGraphRepository();
        repository.upsertFact(GraphFact.active(
            new FactKey(page, RelationType.DECLARES, form, "jsp-form"),
            new EvidenceKey(SourceType.JSP_FALLBACK, "test", "user/edit.jsp", 8, 8, "form"),
            "shop",
            "s1",
            "r1",
            "scope",
            Confidence.CERTAIN,
            SourceType.JSP_FALLBACK
        ));
        repository.upsertFact(GraphFact.active(
            new FactKey(form, RelationType.DECLARES, input, "jsp-input:userId"),
            new EvidenceKey(SourceType.JSP_FALLBACK, "test", "user/edit.jsp", 10, 10, "input-declare"),
            "shop",
            "s1",
            "r1",
            "scope",
            Confidence.CERTAIN,
            SourceType.JSP_FALLBACK
        ));
        repository.upsertFact(GraphFact.active(
            new FactKey(input, RelationType.WRITES_PARAM, parameter, "userId"),
            new EvidenceKey(SourceType.JSP_FALLBACK, "test", "user/edit.jsp", 10, 10, "input"),
            "shop",
            "s1",
            "r1",
            "scope",
            Confidence.CERTAIN,
            SourceType.JSP_FALLBACK
        ));
        repository.upsertFact(GraphFact.active(
            new FactKey(action, RelationType.READS_PARAM, parameter, "request.getParameter:userId"),
            new EvidenceKey(SourceType.SPOON, "test", "UserAction.java", 20, 20, "read"),
            "shop",
            "s1",
            "r1",
            "scope",
            Confidence.LIKELY,
            SourceType.SPOON
        ));
        InMemoryActiveFactStore store = new InMemoryActiveFactStore();
        store.put("shop", "s1", repository.activeFacts("shop", "s1"));
        CodeAtlasHttpServer server = new CodeAtlasHttpServer(new InetSocketAddress(0), new InMemoryReportStore(), new SymbolSearchIndex(), store);
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://localhost:" + server.port();
            String symbolId = URLEncoder.encode(parameter.value(), StandardCharsets.UTF_8);

            HttpResponse<String> source = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/variables/trace-source/report?projectId=shop&snapshotId=s1&symbolId=" + symbolId)).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );
            HttpResponse<String> sink = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/variables/trace-sink/report?projectId=shop&snapshotId=s1&symbolId=" + symbolId)).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );
            HttpResponse<String> combined = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/variables/trace/report?projectId=shop&snapshotId=s1&symbolId=" + symbolId)).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );

            assertTrue(source.body().contains("\"direction\":\"SOURCE\""));
            assertTrue(source.body().contains("WRITES_PARAM"));
            assertTrue(source.body().contains("DECLARES"));
            assertTrue(source.body().contains("user/edit.jsp"));
            assertTrue(sink.body().contains("\"direction\":\"SINK\""));
            assertTrue(sink.body().contains("READS_PARAM"));
            assertTrue(sink.body().contains("UserAction.java"));
            assertTrue(combined.body().contains("\"direction\":\"SOURCE\""));
            assertTrue(combined.body().contains("\"direction\":\"SINK\""));
            assertTrue(combined.body().contains("jsp-input://"));
            assertTrue(combined.body().contains("UserAction.java"));
        } finally {
            server.stop();
        }
    }

    @Test
    void servesNaturalLanguageQueryPlanEndpoint() throws Exception {
        CodeAtlasHttpServer server = new CodeAtlasHttpServer(new InetSocketAddress(0), new InMemoryReportStore());
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://localhost:" + server.port();
            String q = URLEncoder.encode("userId variable where from and where to", StandardCharsets.UTF_8);

            HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/query/plan?q=" + q)).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );

            assertTrue(response.body().contains("\"intent\":\"VARIABLE_TRACE\""));
            assertTrue(response.body().contains("/api/variables/trace/report"));
            assertTrue(response.body().contains("WRITES_PARAM"));
            assertTrue(response.body().contains("READS_PARAM"));
        } finally {
            server.stop();
        }
    }

    @Test
    void servesBusinessQueryResolveEndpoint() throws Exception {
        SymbolId table = SymbolId.logicalPath(SymbolKind.DB_TABLE, "shop", "_root", "_database", "users", null);
        SymbolId sql = SymbolId.logicalPath(SymbolKind.SQL_STATEMENT, "shop", "_root", "src/main/java", "UserJdbcDao#touchUser", "jdbc:10:0");
        InMemoryGraphRepository repository = new InMemoryGraphRepository();
        repository.upsertFact(GraphFact.active(
            new FactKey(sql, RelationType.READS_TABLE, table, "jdbc-table:users"),
            new EvidenceKey(SourceType.SPOON, "jdbc-sql", "UserJdbcDao.java", 10, 10, "select users"),
            "shop",
            "s1",
            "r1",
            "scope",
            Confidence.LIKELY,
            SourceType.SPOON
        ));
        InMemoryActiveFactStore activeStore = new InMemoryActiveFactStore();
        activeStore.put("shop", "s1", repository.activeFacts("shop", "s1"));
        CodeAtlasHttpServer server = new CodeAtlasHttpServer(new InetSocketAddress(0), new InMemoryReportStore(), new SymbolSearchIndex(), activeStore);
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://localhost:" + server.port();
            String q = URLEncoder.encode("users 表被哪些功能使用", StandardCharsets.UTF_8);

            HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/query/resolve?q=" + q + "&projectId=shop&snapshotId=s1")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );

            assertTrue(response.body().contains("\"plan\""));
            assertTrue(response.body().contains("\"intent\":\"SQL_TABLE_IMPACT\""));
            assertTrue(response.body().contains("\"suggestedParameter\":\"symbolId\""));
            assertTrue(response.body().contains(table.value()));
        } finally {
            server.stop();
        }
    }

    @Test
    void servesQueryResultViewContracts() throws Exception {
        CodeAtlasHttpServer server = new CodeAtlasHttpServer(new InetSocketAddress(0), new InMemoryReportStore());
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://localhost:" + server.port();

            HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/query/views")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );

            assertTrue(response.body().contains("IMPACT_REPORT_VIEW"));
            assertTrue(response.body().contains("VARIABLE_TRACE_VIEW"));
            assertTrue(response.body().contains("JSP_FLOW_VIEW"));
            assertTrue(response.body().contains("evidenceFields"));
        } finally {
            server.stop();
        }
    }

    @Test
    void filtersQueryResultViewContractsByName() throws Exception {
        CodeAtlasHttpServer server = new CodeAtlasHttpServer(new InetSocketAddress(0), new InMemoryReportStore());
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://localhost:" + server.port();

            HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/query/views?name=VARIABLE_TRACE_VIEW")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );

            assertTrue(response.body().contains("VARIABLE_TRACE_VIEW"));
            assertTrue(response.body().contains("parameterSymbolId"));
            assertTrue(!response.body().contains("JSP_FLOW_VIEW"));
        } finally {
            server.stop();
        }
    }

    @Test
    void servesJspBackendFlowQueryPlanEndpoint() throws Exception {
        CodeAtlasHttpServer server = new CodeAtlasHttpServer(new InetSocketAddress(0), new InMemoryReportStore());
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://localhost:" + server.port();
            String symbolId = URLEncoder.encode("jsp-page://shop/_root/src/main/webapp/WEB-INF/user.jsp", StandardCharsets.UTF_8);

            HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/jsp/backend-flow/query?projectId=shop&snapshotId=s1&symbolId=" + symbolId + "&maxDepth=4")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );

            assertTrue(response.body().contains("[*1..4]"));
            assertTrue(response.body().contains("SUBMITS_TO"));
            assertTrue(response.body().contains("INCLUDES"));
            assertTrue(response.body().contains("FORWARDS_TO"));
            assertTrue(response.body().contains("WRITES_PARAM"));
        } finally {
            server.stop();
        }
    }

    @Test
    void servesJspBackendFlowReportEndpointFromActiveFacts() throws Exception {
        SymbolId jsp = SymbolId.logicalPath(SymbolKind.JSP_PAGE, "shop", "_root", "src/main/webapp", "user/edit.jsp", null);
        SymbolId actionPath = SymbolId.logicalPath(SymbolKind.ACTION_PATH, "shop", "_root", "src/main/webapp", "user/save", null);
        SymbolId action = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserAction", "execute", "()V");
        SymbolId service = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserService", "save", "()V");
        InMemoryGraphRepository repository = new InMemoryGraphRepository();
        repository.upsertFact(GraphFact.active(
            new FactKey(jsp, RelationType.SUBMITS_TO, actionPath, "/user/save"),
            new EvidenceKey(SourceType.JSP_FALLBACK, "test", "user/edit.jsp", 5, 5, "form"),
            "shop",
            "s1",
            "r1",
            "scope",
            Confidence.CERTAIN,
            SourceType.JSP_FALLBACK
        ));
        repository.upsertFact(GraphFact.active(
            new FactKey(actionPath, RelationType.ROUTES_TO, action, "struts-action"),
            new EvidenceKey(SourceType.STRUTS_CONFIG, "test", "struts-config.xml", 12, 12, "action"),
            "shop",
            "s1",
            "r1",
            "scope",
            Confidence.CERTAIN,
            SourceType.STRUTS_CONFIG
        ));
        repository.upsertFact(GraphFact.active(
            new FactKey(action, RelationType.CALLS, service, "direct"),
            new EvidenceKey(SourceType.SPOON, "test", "UserAction.java", 20, 20, "call"),
            "shop",
            "s1",
            "r1",
            "scope",
            Confidence.LIKELY,
            SourceType.SPOON
        ));
        InMemoryActiveFactStore store = new InMemoryActiveFactStore();
        store.put("shop", "s1", repository.activeFacts("shop", "s1"));
        CodeAtlasHttpServer server = new CodeAtlasHttpServer(new InetSocketAddress(0), new InMemoryReportStore(), new SymbolSearchIndex(), store);
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://localhost:" + server.port();
            String symbolId = URLEncoder.encode(jsp.value(), StandardCharsets.UTF_8);

            HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/jsp/backend-flow/report?projectId=shop&snapshotId=s1&symbolId=" + symbolId + "&maxDepth=4")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );

            assertTrue(response.body().contains("\"startSymbolId\""));
            assertTrue(response.body().contains("SUBMITS_TO"));
            assertTrue(response.body().contains("ROUTES_TO"));
            assertTrue(response.body().contains("CALLS"));
            assertTrue(response.body().contains("UserAction.java"));
        } finally {
            server.stop();
        }
    }

    @Test
    void servesGraphNeighborReportEndpointsFromActiveFacts() throws Exception {
        SymbolId action = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserAction", "execute", "()V");
        SymbolId service = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserService", "save", "()V");
        InMemoryGraphRepository repository = new InMemoryGraphRepository();
        repository.upsertFact(GraphFact.active(
            new FactKey(action, RelationType.CALLS, service, "direct"),
            new EvidenceKey(SourceType.SPOON, "test", "UserAction.java", 20, 20, "call"),
            "shop",
            "s1",
            "r1",
            "scope",
            Confidence.LIKELY,
            SourceType.SPOON
        ));
        InMemoryActiveFactStore store = new InMemoryActiveFactStore();
        store.put("shop", "s1", repository.activeFacts("shop", "s1"));
        CodeAtlasHttpServer server = new CodeAtlasHttpServer(new InetSocketAddress(0), new InMemoryReportStore(), new SymbolSearchIndex(), store);
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://localhost:" + server.port();
            String serviceSymbol = URLEncoder.encode(service.value(), StandardCharsets.UTF_8);
            String actionSymbol = URLEncoder.encode(action.value(), StandardCharsets.UTF_8);

            HttpResponse<String> callers = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/graph/callers/report?projectId=shop&snapshotId=s1&symbolId=" + serviceSymbol + "&maxDepth=2")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );
            HttpResponse<String> callees = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/graph/callees/report?projectId=shop&snapshotId=s1&symbolId=" + actionSymbol + "&maxDepth=2")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );

            assertTrue(callers.body().contains("\"startSymbolId\""));
            assertTrue(callers.body().contains("UserAction.java"));
            assertTrue(callers.body().contains(action.value()));
            assertTrue(callees.body().contains(service.value()));
            assertTrue(callees.body().contains("CALLS"));
        } finally {
            server.stop();
        }
    }

    @Test
    void servesSymbolSearch() throws Exception {
        SymbolSearchIndex index = new SymbolSearchIndex();
        index.add(GraphNodeFactory.methodNode(
            SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserService", "save", "()V"),
            NodeRole.SERVICE
        ));
        CodeAtlasHttpServer server = new CodeAtlasHttpServer(new InetSocketAddress(0), new InMemoryReportStore(), index);
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://localhost:" + server.port();

            HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/symbols/search?q=save")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );

            assertTrue(response.body().contains("\"kind\":\"METHOD\""));
            assertTrue(response.body().contains("UserService#save"));
        } finally {
            server.stop();
        }
    }

    @Test
    void servesImpactAnalyzeReport() throws Exception {
        SymbolId actionPath = SymbolId.logicalPath(SymbolKind.ACTION_PATH, "shop", "_root", "src/main/webapp", "user/save", null);
        SymbolId action = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserAction", "execute", "()V");
        SymbolId service = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserService", "save", "()V");
        InMemoryGraphRepository repository = new InMemoryGraphRepository();
        repository.upsertFact(GraphFact.active(
            new FactKey(actionPath, RelationType.ROUTES_TO, action, "struts-action-execute"),
            new EvidenceKey(SourceType.STRUTS_CONFIG, "test", "struts-config.xml", 1, 1, "route"),
            "shop",
            "s1",
            "r1",
            "scope",
            Confidence.CERTAIN,
            SourceType.STRUTS_CONFIG
        ));
        repository.upsertFact(GraphFact.active(
            new FactKey(action, RelationType.CALLS, service, "direct"),
            new EvidenceKey(SourceType.SPOON, "test", "UserAction.java", 1, 1, "call"),
            "shop",
            "s1",
            "r1",
            "scope",
            Confidence.CERTAIN,
            SourceType.SPOON
        ));
        InMemoryActiveFactStore store = new InMemoryActiveFactStore();
        store.put("shop", "s1", repository.activeFacts("shop", "s1"));
        CodeAtlasHttpServer server = new CodeAtlasHttpServer(new InetSocketAddress(0), new InMemoryReportStore(), new SymbolSearchIndex(), store);
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://localhost:" + server.port();
            String symbol = URLEncoder.encode(service.value(), StandardCharsets.UTF_8);

            HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/impact/analyze?projectId=shop&snapshotId=s1&changedSymbol=" + symbol + "&reportId=r-impact")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );

            assertTrue(response.body().contains("\"reportId\": \"r-impact\""));
            assertTrue(response.body().contains("\"paths\""));
        } finally {
            server.stop();
        }
    }

    @Test
    void impactAnalyzeDiffMapsChangedFilesThroughEvidencePaths() throws Exception {
        SymbolId actionPath = SymbolId.logicalPath(SymbolKind.ACTION_PATH, "shop", "_root", "src/main/webapp", "user/save", null);
        SymbolId action = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserAction", "execute", "()V");
        SymbolId service = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserService", "save", "()V");
        InMemoryGraphRepository repository = new InMemoryGraphRepository();
        repository.upsertFact(GraphFact.active(
            new FactKey(actionPath, RelationType.ROUTES_TO, action, "struts-action-execute"),
            new EvidenceKey(SourceType.STRUTS_CONFIG, "test", "struts-config.xml", 1, 1, "route"),
            "shop",
            "s1",
            "r1",
            "scope",
            Confidence.CERTAIN,
            SourceType.STRUTS_CONFIG
        ));
        repository.upsertFact(GraphFact.active(
            new FactKey(action, RelationType.CALLS, service, "direct"),
            new EvidenceKey(SourceType.SPOON, "test", "src/main/java/com/acme/UserService.java", 11, 11, "call"),
            "shop",
            "s1",
            "r1",
            "scope",
            Confidence.CERTAIN,
            SourceType.SPOON
        ));
        InMemoryActiveFactStore store = new InMemoryActiveFactStore();
        store.put("shop", "s1", repository.activeFacts("shop", "s1"));
        CodeAtlasHttpServer server = new CodeAtlasHttpServer(new InetSocketAddress(0), new InMemoryReportStore(), new SymbolSearchIndex(), store);
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://localhost:" + server.port();
            String diff = """
                diff --git a/src/main/java/com/acme/UserService.java b/src/main/java/com/acme/UserService.java
                --- a/src/main/java/com/acme/UserService.java
                +++ b/src/main/java/com/acme/UserService.java
                @@ -1 +1 @@
                -old
                +new
                """;
            String encodedDiff = URLEncoder.encode(diff, StandardCharsets.UTF_8);

            HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/impact/analyze-diff?projectId=shop&snapshotId=s1&diffText=" + encodedDiff + "&reportId=r-diff")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );

            assertTrue(response.body().contains("\"reportId\": \"r-diff\""));
            assertTrue(response.body().contains("action-path://shop/_root/src/main/webapp/user/save"));
            assertTrue(response.body().contains("CALLS"));
            assertTrue(response.body().contains("src/main/java/com/acme/UserService.java"));
        } finally {
            server.stop();
        }
    }

    @Test
    void impactAnalyzeParsesJspSymbolAndReportsIncludingPage() throws Exception {
        SymbolId page = SymbolId.logicalPath(SymbolKind.JSP_PAGE, "shop", "_root", "src/main/webapp", "user/edit.jsp", null);
        SymbolId include = SymbolId.logicalPath(SymbolKind.JSP_PAGE, "shop", "_root", "src/main/webapp", "common/footer.jsp", null);
        InMemoryGraphRepository repository = new InMemoryGraphRepository();
        repository.upsertFact(GraphFact.active(
            new FactKey(page, RelationType.INCLUDES, include, "static_directive:/common/footer.jsp"),
            new EvidenceKey(SourceType.JSP_FALLBACK, "test", "user/edit.jsp", 2, 2, "include"),
            "shop",
            "s1",
            "r1",
            "scope",
            Confidence.CERTAIN,
            SourceType.JSP_FALLBACK
        ));
        InMemoryActiveFactStore store = new InMemoryActiveFactStore();
        store.put("shop", "s1", repository.activeFacts("shop", "s1"));
        CodeAtlasHttpServer server = new CodeAtlasHttpServer(new InetSocketAddress(0), new InMemoryReportStore(), new SymbolSearchIndex(), store);
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://localhost:" + server.port();
            String symbol = URLEncoder.encode(include.value(), StandardCharsets.UTF_8);

            HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/impact/analyze?projectId=shop&snapshotId=s1&changedSymbol=" + symbol + "&reportId=r-jsp")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );

            assertTrue(response.body().contains("\"reportId\": \"r-jsp\""));
            assertTrue(response.body().contains("jsp-page://shop/_root/src/main/webapp/user/edit.jsp"));
            assertTrue(response.body().contains("INCLUDES"));
        } finally {
            server.stop();
        }
    }
}
