package org.sainm.codeatlas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QueryResultViewRegistryTest {
    @Test
    void exposesStableResultViewContracts() {
        QueryResultViewRegistry registry = new QueryResultViewRegistry();

        assertEquals(8, registry.listViews().size());
        assertTrue(registry.find("PROJECT_OVERVIEW_VIEW").orElseThrow().primaryFields().contains("capabilities"));
        assertTrue(registry.find("PROJECT_OVERVIEW_VIEW").orElseThrow().primaryFields().contains("analysisStatus"));
        assertTrue(registry.find("IMPACT_REPORT_VIEW").orElseThrow().primaryFields().contains("riskLevel"));
        assertTrue(registry.find("VARIABLE_TRACE_VIEW").orElseThrow().primaryFields().contains("direction"));
        assertTrue(registry.find("JSP_FLOW_VIEW").orElseThrow().primaryFields().contains("path"));
        assertTrue(registry.find("GRAPH_NEIGHBOR_VIEW").orElseThrow().primaryFields().contains("path"));
        assertTrue(registry.find("GRAPH_NEIGHBOR_VIEW").orElseThrow().evidenceFields().contains("evidenceKeys"));
        assertTrue(registry.find("RAG_SEARCH_VIEW").orElseThrow().primaryFields().contains("matchKinds"));
        assertTrue(registry.find("RAG_SEARCH_VIEW").orElseThrow().evidenceFields().contains("evidenceKeys"));
        assertTrue(registry.find("SYMBOL_PICKER_VIEW").orElseThrow().primaryFields().contains("score"));
    }
}
