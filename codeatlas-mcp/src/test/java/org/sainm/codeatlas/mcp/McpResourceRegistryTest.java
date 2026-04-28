package org.sainm.codeatlas.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class McpResourceRegistryTest {
    @Test
    void defaultRegistryExposesReadOnlyResources() {
        McpResourceRegistry registry = McpResourceRegistry.defaultReadOnlyRegistry();

        assertEquals(McpResourceName.values().length, registry.listResources().size());
        for (McpResourceName name : McpResourceName.values()) {
            assertTrue(registry.isAllowed(name));
            assertTrue(registry.find(name).orElseThrow().readOnly());
        }
        assertTrue(registry.find(McpResourceName.REPORT).orElseThrow().uriTemplate().contains("{reportId}"));
    }
}
