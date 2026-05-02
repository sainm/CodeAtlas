package org.sainm.codeatlas.server;

import org.sainm.codeatlas.graph.CodeAtlasGraph;

public final class CodeAtlasServer {
    private CodeAtlasServer() {
    }

    public static HealthResponse health() {
        return HealthResponse.ok();
    }

    public static String dependencySummary() {
        return CodeAtlasGraph.moduleName();
    }

    public static void main(String[] args) {
        HealthResponse health = health();
        System.out.println(health.status() + " " + health.service());
    }
}
