package org.sainm.codeatlas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CodeAtlasServerTest {
    @LocalServerPort
    private int port;

    @Test
    void healthReturnsOkStatus() {
        HealthResponse health = CodeAtlasServer.health();

        assertEquals("ok", health.status());
        assertEquals("codeatlas-server", health.service());
    }

    @Test
    void healthEndpointReturnsVersionedJson() throws Exception {
        HttpResponse<String> response = get("/api/v1/health");

        assertEquals(200, response.statusCode());
        assertEquals("application/json", response.headers().firstValue("content-type").orElse(""));
        assertEquals("{\"status\":\"ok\",\"service\":\"codeatlas-server\"}", response.body());
    }

    @Test
    void healthEndpointDoesNotMatchPrefixPaths() throws Exception {
        HttpResponse<String> response = get("/api/v1/healthcheck");

        assertEquals(404, response.statusCode());
    }

    @Test
    void readOnlyQueryContractsUseV1AndReturnSnapshotEnvelope() throws Exception {
        for (String path : new String[] {
                "/api/v1/workspaces",
                "/api/v1/projects",
                "/api/v1/import-reviews",
                "/api/v1/analysis-runs",
                "/api/v1/snapshots",
                "/api/v1/projects/overview?projectId=shop",
                "/api/v1/symbols/search?q=User",
                "/api/v1/graph/callers?symbolId=method://shop/_root/src/main/java/com.acme.A#a()V",
                "/api/v1/graph/callees?symbolId=method://shop/_root/src/main/java/com.acme.A#a()V",
                "/api/v1/graph/paths?from=a&to=b",
                "/api/v1/impact/reports",
                "/api/v1/variable-trace?symbolId=request-param://shop/_root/_api%23id",
                "/api/v1/db-impact?identityId=db-table://shop/mainDs/public/users",
                "/api/v1/architecture-health",
                "/api/v1/jsp-backend-flow?identityId=jsp-page://shop/_root/src/main/webapp/index.jsp",
                "/api/v1/sql-table-impact?identityId=db-table://shop/mainDs/public/users",
                "/api/v1/reports",
                "/api/v1/evidence?evidenceKey=evidence-1",
                "/api/v1/saved-queries",
                "/api/v1/subscriptions",
                "/api/v1/review-threads",
                "/api/v1/policies",
                "/api/v1/ci-checks",
                "/api/v1/exports",
                "/api/v1/query/plan?q=find%20callers",
                "/api/v1/result-view-contract"
        }) {
            HttpResponse<String> response = get(path);

            assertEquals(200, response.statusCode(), path + " " + response.body());
            assertTrue(response.body().contains("\"snapshotId\":\"latest-committed\""), path);
        }
    }

    @Test
    void asyncEndpointsReturnJobAndReportArtifactIds() throws Exception {
        HttpResponse<String> impact = post("/api/v1/impact/analyze-diff");
        HttpResponse<String> change = post("/api/v1/features/plan-change");
        HttpResponse<String> addition = post("/api/v1/features/plan-addition");

        assertEquals(200, impact.statusCode());
        assertTrue(impact.body().contains("\"jobId\":\"job-impact-1\""));
        assertTrue(change.body().contains("\"reportArtifactId\":\"change-plan-1\""));
        assertTrue(addition.body().contains("\"status\":\"QUEUED\""));
    }

    @Test
    void listLimitErrorsAreStructured() throws Exception {
        HttpResponse<String> response = get("/api/v1/projects?limit=101");

        assertEquals(400, response.statusCode());
        assertStructuredError(response.body(), "LIMIT_EXCEEDED", 400);
    }

    @Test
    void invalidAndMissingParametersReturnStructuredErrors() throws Exception {
        HttpResponse<String> response = get("/api/v1/symbols/search?q=");

        assertEquals(400, response.statusCode());
        assertStructuredError(response.body(), "MISSING_QUERY", 400);
    }

    @Test
    void projectAllowListReturnsForbiddenForUnknownProjects() throws Exception {
        HttpResponse<String> response = get("/api/v1/projects/overview?projectId=blocked");

        assertEquals(403, response.statusCode());
        assertStructuredError(response.body(), "PROJECT_FORBIDDEN", 403);
    }

    @Test
    void managementOperationsRequireConfirmationOrIdempotencyKey() throws Exception {
        HttpResponse<String> rejected = delete("/api/v1/admin/projects?projectId=shop");
        HttpResponse<String> confirmed = delete("/api/v1/admin/projects?projectId=shop&confirm=true");

        assertEquals(400, rejected.statusCode());
        assertStructuredError(rejected.body(), "CONFIRMATION_REQUIRED", 400);
        assertEquals(200, confirmed.statusCode());
        assertTrue(confirmed.body().contains("\"status\":\"accepted\""));
    }

    @Test
    void localFrontendCorsOriginIsAllowed() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/projects"))
                .header("Origin", "http://localhost:5173")
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("http://localhost:5173", response.headers().firstValue("access-control-allow-origin").orElse(""));
    }

    private static void assertStructuredError(String body, String code, int status) {
        assertTrue(body.contains("\"requestId\":\""));
        assertTrue(body.contains("\"code\":\"" + code + "\""), body);
        assertTrue(body.contains("\"message\":\""));
        assertTrue(body.contains("\"details\":"));
        assertTrue(body.contains("\"retryable\":false"));
        assertTrue(body.contains("\"status\":" + status));
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .GET()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .DELETE()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
