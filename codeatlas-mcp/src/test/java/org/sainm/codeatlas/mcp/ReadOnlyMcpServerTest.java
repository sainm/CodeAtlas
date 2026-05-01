package org.sainm.codeatlas.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @Test
    void rejectsArbitraryDatabaseQueryArgumentsEvenForAllowedTools() {
        ReadOnlyMcpServer server = new ReadOnlyMcpServer();

        assertThrows(
            IllegalArgumentException.class,
            () -> server.planToolCall(McpToolName.SYMBOL_SEARCH, Map.of("q", "User", "rawCypher", "MATCH (n) RETURN n"))
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> server.planToolCall(McpToolName.QUERY_PLAN, Map.of("q", "find callers", "sql", "select * from users"))
        );
    }

    @Test
    void safetyGuardRequiresExplicitConfirmationForWriteTools() {
        McpToolCallSafetyGuard guard = new McpToolCallSafetyGuard();
        McpToolDescriptor writeTool = new McpToolDescriptor(
            McpToolName.REPORT_GET_IMPACT_REPORT,
            "hypothetical write-capable tool",
            false,
            30,
            "{}"
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> guard.validate(writeTool, Map.of())
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> guard.validate(writeTool, Map.of("confirmWrite", true, "confirmationIntent", "READ_ONLY"))
        );
        guard.validate(writeTool, Map.of("confirmWrite", true, "confirmationIntent", "ALLOW_WRITE"));
    }

    @Test
    void rejectsToolCallsForProjectsOutsideTheCallerScope() {
        ReadOnlyMcpServer server = new ReadOnlyMcpServer();
        McpRequestContext context = McpRequestContext.forProjects("alice", Set.of("project-a"));

        assertThrows(
            IllegalArgumentException.class,
            () -> server.planToolCall(
                McpToolName.GRAPH_FIND_CALLEES,
                Map.of("projectId", "project-b", "snapshotId", "s1", "symbolId", "method:UserService#save()V"),
                context
            )
        );
    }

    @Test
    void rateLimitsToolPlanningByPrincipal() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-01T00:00:00Z"), ZoneOffset.UTC);
        McpInMemoryRateLimiter rateLimiter = new McpInMemoryRateLimiter(2, Duration.ofMinutes(1), fixedClock);
        ReadOnlyMcpServer server = new ReadOnlyMcpServer(
            McpToolRegistry.defaultReadOnlyRegistry(),
            McpResourceRegistry.defaultReadOnlyRegistry(),
            McpPromptRegistry.defaultRegistry(),
            rateLimiter,
            new McpAuditLog()
        );
        McpRequestContext context = McpRequestContext.allowAllProjects("alice");

        server.planToolCall(McpToolName.SYMBOL_SEARCH, Map.of("q", "User"), context);
        server.planToolCall(McpToolName.SYMBOL_SEARCH, Map.of("q", "Account"), context);

        assertThrows(
            IllegalStateException.class,
            () -> server.planToolCall(McpToolName.SYMBOL_SEARCH, Map.of("q", "Order"), context)
        );
    }

    @Test
    void auditLogRedactsSensitiveToolArguments() {
        McpAuditLog auditLog = new McpAuditLog();
        ReadOnlyMcpServer server = new ReadOnlyMcpServer(
            McpToolRegistry.defaultReadOnlyRegistry(),
            McpResourceRegistry.defaultReadOnlyRegistry(),
            McpPromptRegistry.defaultRegistry(),
            McpInMemoryRateLimiter.unlimited(),
            auditLog
        );

        server.planToolCall(
            McpToolName.IMPACT_ANALYZE_DIFF,
            Map.of("projectId", "project-a", "snapshotId", "s1", "diffText", "password=secret token=abc123"),
            McpRequestContext.allowAllProjects("alice")
        );

        assertEquals(1, auditLog.events().size());
        String redactedArguments = auditLog.events().getFirst().redactedArguments().toString();
        assertTrue(redactedArguments.contains("password=[REDACTED]"));
        assertTrue(redactedArguments.contains("token=[REDACTED]"));
    }
}
