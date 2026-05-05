package org.sainm.codeatlas.mcp;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpContractsTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void catalogDefinesReadOnlyToolsResourcesAndPrompts() {
        McpContracts.McpCatalog catalog = McpContracts.McpCatalog.defaults();

        assertEquals(14, catalog.tools().size());
        assertTrue(catalog.tools().stream().allMatch(McpContracts.McpToolDefinition::readOnly));
        assertTrue(catalog.resources().stream().anyMatch(resource -> resource.uri().equals("codeatlas://evidence")));
        assertTrue(catalog.prompts().stream().anyMatch(prompt -> prompt.name().equals("impact-analysis")));
        assertEquals("db.findCodeImpacts", catalog.requireTool("db.findCodeImpacts").name());
    }

    @Test
    void agentProfilesExposeExpectedToolsAndOutputContracts() {
        McpContracts.AgentProfileRegistry registry = McpContracts.AgentProfileRegistry.defaults();

        assertTrue(registry.require(McpContracts.AgentType.IMPACT_ANALYSIS).allowedTools().contains("impact.analyzeDiff"));
        assertTrue(registry.require(McpContracts.AgentType.DB_IMPACT).outputContract().contains("table fallback"));
        assertTrue(registry.require(McpContracts.AgentType.VARIABLE_IMPACT).outputContract().contains("sources"));
        assertTrue(registry.require(McpContracts.AgentType.FEATURE_ADDITION_PLAN).allowedTools().contains("feature.planAddition"));
        assertTrue(registry.require(McpContracts.AgentType.CODE_QUESTION).allowedTools().contains("rag.answerDraft"));
    }

    @Test
    void agentStateMachineCarriesRequiredFieldsAndDeepSupplementMarksUpgrade() {
        McpContracts.AgentRunPlanner planner = new McpContracts.AgentRunPlanner(clock);

        McpContracts.AgentRunState fast = planner.fastReady(
                McpContracts.AgentType.IMPACT_ANALYSIS,
                "shop",
                "snap-1",
                "q1",
                List.of(Map.of("risk", "medium")));
        McpContracts.DeepSupplement supplement = planner.deepSupplement(fast);
        McpContracts.AgentRunState partial = planner.partialFailure(fast, "deep timeout");

        assertEquals(McpContracts.AgentStatus.FAST_READY, fast.status());
        assertEquals("agent-q1", fast.agentRunId());
        assertEquals(List.of("deep-q1"), fast.deepJobIds());
        assertTrue(supplement.originalReportStale());
        assertTrue(supplement.upgradeAvailable());
        assertEquals(McpContracts.AgentStatus.PARTIAL, partial.status());
        assertEquals("deep timeout", partial.errors().getFirst());
    }

    @Test
    void candidateResolverReturnsPickerWhenScopesDiffer() {
        McpContracts.CandidateResolver resolver = new McpContracts.CandidateResolver();
        List<McpContracts.CandidateOption> candidates = List.of(
                candidate("shop", "web", "mainDs"),
                candidate("shop", "batch", "mainDs"));

        McpContracts.CandidateDecision decision = resolver.decide(candidates);

        assertTrue(decision.needsPicker());
        assertTrue(decision.followUpQuestion().orElseThrow().contains("project/module/datasource"));
        assertEquals(2, decision.candidates().size());
    }

    @Test
    void candidateResolverReturnsPickerForSameScopeAmbiguities() {
        McpContracts.CandidateResolver resolver = new McpContracts.CandidateResolver();
        List<McpContracts.CandidateOption> candidates = List.of(
                new McpContracts.CandidateOption("shop", "web", "mainDs", "Method", "src/UserService.java", 12, "LIKELY", "ev-1"),
                new McpContracts.CandidateOption("shop", "web", "mainDs", "Method", "src/UserController.java", 18, "LIKELY", "ev-2"));

        McpContracts.CandidateDecision decision = resolver.decide(candidates);

        assertTrue(decision.needsPicker());
        assertTrue(decision.followUpQuestion().orElseThrow().contains("choose one candidate"));
        assertEquals(2, decision.candidates().size());
    }

    @Test
    void toolGuardRejectsOutOfProfileUnsafeArgumentsDisallowedProjectsAndRateLimit() {
        McpContracts.McpCatalog catalog = McpContracts.McpCatalog.defaults();
        McpContracts.RedactedAuditLog auditLog = new McpContracts.RedactedAuditLog();
        McpContracts.ToolCallGuard guard = new McpContracts.ToolCallGuard(
                Set.of("shop"),
                new McpContracts.FixedWindowRateLimiter(1, Duration.ofMinutes(1)),
                auditLog,
                clock);
        McpContracts.AgentProfile profile = McpContracts.AgentProfileRegistry.defaults().require(McpContracts.AgentType.CODE_QUESTION);

        assertFalse(guard.authorize(profile, catalog.requireTool("impact.analyzeDiff"), request("shop", "impact.analyzeDiff", Map.of("query", "x"))).allowed());
        assertFalse(guard.authorize(profile, catalog.requireTool("rag.answerDraft"), request("other", "rag.answerDraft", Map.of("query", "x"))).allowed());
        assertFalse(guard.authorize(profile, catalog.requireTool("rag.answerDraft"), request("shop", "rag.answerDraft", Map.of("sql", "select * from users"))).allowed());

        McpContracts.ToolCallGuard rateGuard = new McpContracts.ToolCallGuard(
                Set.of("shop"),
                new McpContracts.FixedWindowRateLimiter(1, Duration.ofMinutes(1)),
                auditLog,
                clock);
        assertTrue(rateGuard.authorize(profile, catalog.requireTool("rag.answerDraft"), request("shop", "rag.answerDraft", Map.of("query", "email"))).allowed());
        assertFalse(rateGuard.authorize(profile, catalog.requireTool("rag.answerDraft"), request("shop", "rag.answerDraft", Map.of("query", "name"))).allowed());
    }

    @Test
    void readOnlyServerDispatchesAndWritesRedactedAuditLog() {
        McpContracts.McpCatalog catalog = McpContracts.McpCatalog.defaults();
        McpContracts.RedactedAuditLog auditLog = new McpContracts.RedactedAuditLog();
        McpContracts.ToolCallGuard guard = new McpContracts.ToolCallGuard(
                Set.of("shop"),
                new McpContracts.FixedWindowRateLimiter(10, Duration.ofMinutes(1)),
                auditLog,
                clock);
        McpContracts.ReadOnlyMcpServer server = new McpContracts.ReadOnlyMcpServer(catalog, guard);
        server.register("rag.answerDraft", request -> McpContracts.ToolResponse.ok(
                List.of(Map.of("answer", "UserService writes users.email")),
                List.of(new McpContracts.AgentEvidence("ev-1", "src/UserService.java", 12, "LIKELY", "STATIC")),
                false));

        McpContracts.ToolResponse response = server.dispatch(
                McpContracts.AgentProfileRegistry.defaults().require(McpContracts.AgentType.CODE_QUESTION),
                request("shop", "rag.answerDraft", Map.of("query", "email=a@example.com token=secret")));

        assertTrue(response.accepted());
        assertFalse(response.truncated());
        assertEquals("LIKELY", response.evidence().getFirst().confidence());
        assertEquals("STATIC", response.evidence().getFirst().sourceType());
        assertEquals(1, auditLog.entries().size());
        assertTrue(auditLog.entries().getFirst().parameterSummary().contains("[REDACTED_EMAIL]"));
        assertTrue(auditLog.entries().getFirst().parameterSummary().contains("[REDACTED_SECRET]"));
        assertEquals(Optional.empty(), auditLog.entries().getFirst().rejectionReason());
    }

    @Test
    void readOnlyServerRejectsUnknownToolsWithAuditEntry() {
        McpContracts.RedactedAuditLog auditLog = new McpContracts.RedactedAuditLog();
        McpContracts.ReadOnlyMcpServer server = new McpContracts.ReadOnlyMcpServer(
                McpContracts.McpCatalog.defaults(),
                new McpContracts.ToolCallGuard(
                        Set.of("shop"),
                        new McpContracts.FixedWindowRateLimiter(10, Duration.ofMinutes(1)),
                        auditLog,
                        clock));

        McpContracts.ToolResponse response = server.dispatch(
                McpContracts.AgentProfileRegistry.defaults().require(McpContracts.AgentType.CODE_QUESTION),
                request("shop", "shell.run", Map.of("query", "x")));

        assertFalse(response.accepted());
        assertEquals(Optional.of("unknown MCP tool"), response.rejectionReason());
        assertEquals(1, auditLog.entries().size());
        assertEquals("shell.run", auditLog.entries().getFirst().toolName());
        assertEquals(Optional.of("unknown MCP tool"), auditLog.entries().getFirst().rejectionReason());
    }

    @Test
    void agentOutputRequiresEvidenceConfidenceSourceTypeAndTruncation() {
        McpContracts.AgentOutput output = new McpContracts.AgentOutput(
                List.of(Map.of("path", "EntryPoint -> SQL")),
                List.of(new McpContracts.AgentEvidence("ev-1", "mapper/UserMapper.xml", 18, "CERTAIN", "STATIC_SQL")),
                "CERTAIN",
                "STATIC_SQL",
                true,
                "TRUNCATED");

        assertTrue(output.truncated());
        assertEquals("ev-1", output.evidence().getFirst().evidenceKey());
        assertEquals("TRUNCATED", output.status());
    }

    @Test
    void slidingWindowPreventsBoundaryBurst() {
        Instant start = clock.instant();
        McpContracts.FixedWindowRateLimiter limiter = new McpContracts.FixedWindowRateLimiter(2, Duration.ofSeconds(10));

        assertTrue(limiter.tryAcquire("alice", start));
        assertTrue(limiter.tryAcquire("alice", start.plusMillis(1)));
        assertFalse(limiter.tryAcquire("alice", start.plusMillis(2)));

        Instant nearEnd = start.plusSeconds(9).plusMillis(999);
        assertFalse(limiter.tryAcquire("alice", nearEnd));
    }

    @Test
    void slidingWindowAllowsRequestsAfterOldEntriesExpire() {
        Instant start = clock.instant();
        McpContracts.FixedWindowRateLimiter limiter = new McpContracts.FixedWindowRateLimiter(2, Duration.ofSeconds(10));

        assertTrue(limiter.tryAcquire("alice", start));
        assertTrue(limiter.tryAcquire("alice", start.plusSeconds(1)));

        Instant afterWindow = start.plusSeconds(10).plusMillis(1);
        assertTrue(limiter.tryAcquire("alice", afterWindow));
    }

    @Test
    void slidingWindowTracksPrincipalsIndependently() {
        Instant start = clock.instant();
        McpContracts.FixedWindowRateLimiter limiter = new McpContracts.FixedWindowRateLimiter(1, Duration.ofSeconds(10));

        assertTrue(limiter.tryAcquire("alice", start));
        assertFalse(limiter.tryAcquire("alice", start.plusMillis(1)));
        assertTrue(limiter.tryAcquire("bob", start.plusMillis(1)));
    }

    private static McpContracts.CandidateOption candidate(String project, String module, String datasource) {
        return new McpContracts.CandidateOption(project, module, datasource, "Method", "src/User.java", 12, "LIKELY", "ev-1");
    }

    private static McpContracts.ToolRequest request(String project, String toolName, Map<String, String> arguments) {
        return new McpContracts.ToolRequest("req-1", "alice", project, toolName, arguments);
    }
}
