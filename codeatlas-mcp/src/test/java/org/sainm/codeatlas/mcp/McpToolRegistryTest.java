package org.sainm.codeatlas.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class McpToolRegistryTest {
    @Test
    void defaultRegistryExposesAllToolsAsReadOnly() {
        McpToolRegistry registry = McpToolRegistry.defaultReadOnlyRegistry();

        assertEquals(McpToolName.values().length, registry.listTools().size());
        for (McpToolName name : McpToolName.values()) {
            assertTrue(registry.isAllowed(name));
            assertTrue(registry.find(name).orElseThrow().readOnly());
        }
    }

    @Test
    void impactQueriesUseLongerTimeout() {
        McpToolRegistry registry = McpToolRegistry.defaultReadOnlyRegistry();

        assertEquals(60, registry.find(McpToolName.GRAPH_FIND_IMPACT_PATHS).orElseThrow().timeoutSeconds());
        assertEquals(60, registry.find(McpToolName.IMPACT_ANALYZE_DIFF).orElseThrow().timeoutSeconds());
    }

    @Test
    void exposesInputSchemasForCoreQueryTools() {
        McpToolRegistry registry = McpToolRegistry.defaultReadOnlyRegistry();

        assertTrue(registry.find(McpToolName.SYMBOL_SEARCH).orElseThrow().inputSchema().contains("\"q\""));
        assertTrue(registry.find(McpToolName.GRAPH_FIND_CALLERS).orElseThrow().inputSchema().contains("\"symbolId\""));
        assertTrue(registry.find(McpToolName.VARIABLE_TRACE_SOURCE).orElseThrow().inputSchema().contains("REQUEST_PARAMETER"));
        assertTrue(registry.find(McpToolName.JSP_FIND_BACKEND_FLOW).orElseThrow().inputSchema().contains("JSP_PAGE"));
        assertTrue(registry.find(McpToolName.IMPACT_ANALYZE_DIFF).orElseThrow().inputSchema().contains("diffText"));
        assertTrue(registry.find(McpToolName.REPORT_GET_IMPACT_REPORT).orElseThrow().inputSchema().contains("\"reportId\""));
    }
}
