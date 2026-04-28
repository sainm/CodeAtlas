package org.sainm.codeatlas.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class McpPromptRegistryTest {
    @Test
    void defaultRegistryExposesEvidenceOrientedPrompts() {
        McpPromptRegistry registry = McpPromptRegistry.defaultRegistry();

        assertEquals(McpPromptName.values().length, registry.listPrompts().size());
        assertTrue(registry.find(McpPromptName.IMPACT_REVIEW).orElseThrow().template().contains("evidence"));
        assertTrue(registry.find(McpPromptName.VARIABLE_TRACE).orElseThrow().requiredArguments().contains("symbolId"));
        assertTrue(registry.find(McpPromptName.JSP_FLOW_ANALYSIS).orElseThrow().template().contains("backend path"));
        assertTrue(registry.find(McpPromptName.TEST_RECOMMENDATION).orElseThrow().requiredArguments().contains("reportId"));
    }
}
