package org.sainm.codeatlas.server;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.graph.impact.ImpactReport;
import org.sainm.codeatlas.graph.impact.ReportDepth;
import org.sainm.codeatlas.graph.model.GraphNodeFactory;
import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.EvidenceKey;
import org.sainm.codeatlas.graph.model.FactKey;
import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.NodeRole;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
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
            assertTrue(report.body().contains("\"reportId\": \"r1\""));
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
        SymbolId action = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserAction", "execute", "()V");
        SymbolId service = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserService", "save", "()V");
        InMemoryGraphRepository repository = new InMemoryGraphRepository();
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
}
