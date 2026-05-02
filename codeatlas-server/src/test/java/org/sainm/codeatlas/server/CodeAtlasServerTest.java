package org.sainm.codeatlas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .GET()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
