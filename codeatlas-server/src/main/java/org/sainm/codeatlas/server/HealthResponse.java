package org.sainm.codeatlas.server;

public record HealthResponse(String status, String service) {
    public static HealthResponse ok() {
        return new HealthResponse("ok", "codeatlas-server");
    }
}
