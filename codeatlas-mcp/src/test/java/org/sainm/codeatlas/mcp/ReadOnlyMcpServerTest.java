package org.sainm.codeatlas.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReadOnlyMcpServerTest {
    @Test
    void exposesReadOnlyCapabilitiesAndCatalogs() {
        ReadOnlyMcpServer server = new ReadOnlyMcpServer();

        assertTrue(server.initialize().readOnly());
        assertEquals(McpToolName.values().length, server.listTools().size());
        assertEquals(McpResourceName.values().length, server.listResources().size());
        assertEquals(McpPromptName.values().length, server.listPrompts().size());
    }

    @Test
    void plansAllowedToolCallsWithoutExecutingArbitraryWork() {
        ReadOnlyMcpServer server = new ReadOnlyMcpServer();

        McpToolCallPlan plan = server.planToolCall(McpToolName.SYMBOL_SEARCH, Map.of("q", "UserService"));

        assertEquals(McpToolName.SYMBOL_SEARCH, plan.descriptor().name());
        assertEquals("UserService", plan.arguments().get("q"));
    }

    @Test
    void rejectsToolsNotInReadOnlyRegistry() {
        McpToolRegistry restrictedTools = new McpToolRegistry(List.of());
        ReadOnlyMcpServer server = new ReadOnlyMcpServer(
            restrictedTools,
            McpResourceRegistry.defaultReadOnlyRegistry(),
            McpPromptRegistry.defaultRegistry()
        );

        assertThrows(IllegalArgumentException.class, () -> server.planToolCall(McpToolName.SYMBOL_SEARCH, Map.of()));
    }
}
