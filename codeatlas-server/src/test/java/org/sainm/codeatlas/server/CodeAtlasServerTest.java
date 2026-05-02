package org.sainm.codeatlas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CodeAtlasServerTest {
    @Test
    void healthReturnsOkStatus() {
        HealthResponse health = CodeAtlasServer.health();

        assertEquals("ok", health.status());
        assertEquals("codeatlas-server", health.service());
    }
}
