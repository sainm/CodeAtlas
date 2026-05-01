package org.sainm.codeatlas.ai.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentRegistryTest {
    @Test
    void defaultToolRegistryIsReadOnlyAndBounded() {
        AgentToolRegistry registry = AgentToolRegistry.defaultReadOnlyRegistry();

        assertTrue(registry.isAllowed(AgentToolName.QUERY_PLAN));
        assertTrue(registry.isAllowed(AgentToolName.JSP_FIND_BACKEND_FLOW));
        assertEquals(12, registry.listTools().size());
        assertEquals(60, registry.find(AgentToolName.IMPACT_ANALYZE_DIFF).orElseThrow().timeoutSeconds());
    }

    @Test
    void profilesExposeOnlyAgentSpecificReadOnlyTools() {
        AgentToolRegistry tools = AgentToolRegistry.defaultReadOnlyRegistry();
        AgentProfileRegistry profiles = AgentProfileRegistry.defaultProfiles();

        AgentProfile impact = profiles.find(AgentType.IMPACT_ANALYSIS).orElseThrow();
        AgentProfile variable = profiles.find(AgentType.VARIABLE_TRACE).orElseThrow();

        assertTrue(impact.evidenceRequired());
        assertTrue(impact.allows(tools, AgentToolName.IMPACT_ANALYZE_DIFF));
        assertFalse(impact.allows(tools, AgentToolName.VARIABLE_TRACE_SOURCE));
        assertTrue(variable.allows(tools, AgentToolName.VARIABLE_TRACE));
        assertTrue(variable.allows(tools, AgentToolName.VARIABLE_TRACE_SINK));
        assertFalse(variable.allows(tools, AgentToolName.IMPACT_ANALYZE_DIFF));
    }

    @Test
    void callGuardRejectsOutOfProfileAndOverLimitTools() {
        AgentToolCallGuard guard = new AgentToolCallGuard();
        AgentToolRegistry tools = AgentToolRegistry.defaultReadOnlyRegistry();
        AgentProfile variable = AgentProfileRegistry.defaultProfiles()
            .find(AgentType.VARIABLE_TRACE)
            .orElseThrow();

        assertTrue(guard.validate(variable, tools, AgentToolName.VARIABLE_TRACE, 0).allowed());
        assertFalse(guard.validate(variable, tools, AgentToolName.IMPACT_ANALYZE_DIFF, 0).allowed());
        assertFalse(guard.validate(variable, tools, AgentToolName.VARIABLE_TRACE_SOURCE, variable.maxToolCalls()).allowed());
    }

    @Test
    void callGuardRequiresExplicitConfirmationForWriteTools() {
        AgentToolCallGuard guard = new AgentToolCallGuard();
        AgentToolRegistry tools = new AgentToolRegistry(List.of(new AgentToolDescriptor(
            AgentToolName.REPORT_GET_IMPACT_REPORT,
            "hypothetical write-capable tool",
            false,
            30
        )));
        AgentProfile profile = new AgentProfile(
            AgentType.IMPACT_ANALYSIS,
            List.of(AgentToolName.REPORT_GET_IMPACT_REPORT),
            3,
            30,
            true
        );

        AgentToolCallDecision missingConfirmation = guard.validate(profile, tools, AgentToolName.REPORT_GET_IMPACT_REPORT, 0, Map.of());
        AgentToolCallDecision wrongIntent = guard.validate(
            profile,
            tools,
            AgentToolName.REPORT_GET_IMPACT_REPORT,
            0,
            Map.of("confirmWrite", true, "confirmationIntent", "READ_ONLY")
        );
        AgentToolCallDecision confirmed = guard.validate(
            profile,
            tools,
            AgentToolName.REPORT_GET_IMPACT_REPORT,
            0,
            Map.of("confirmWrite", true, "confirmationIntent", "ALLOW_WRITE")
        );

        assertFalse(missingConfirmation.allowed());
        assertEquals("write tool requires explicit confirmation", missingConfirmation.reason());
        assertFalse(wrongIntent.allowed());
        assertTrue(confirmed.allowed());
    }

    @Test
    void auditLogRecordsAllowedAndDeniedToolDecisions() {
        AgentToolCallGuard guard = new AgentToolCallGuard();
        AgentToolRegistry tools = AgentToolRegistry.defaultReadOnlyRegistry();
        AgentProfile variable = AgentProfileRegistry.defaultProfiles()
            .find(AgentType.VARIABLE_TRACE)
            .orElseThrow();
        AgentAuditLog auditLog = new AgentAuditLog();

        AgentToolCallDecision allowed = guard.validate(variable, tools, AgentToolName.VARIABLE_TRACE, 0);
        AgentToolCallDecision denied = guard.validate(variable, tools, AgentToolName.IMPACT_ANALYZE_DIFF, 1);
        auditLog.record("run-1", variable, AgentToolName.VARIABLE_TRACE, allowed, 0);
        auditLog.record("run-1", variable, AgentToolName.IMPACT_ANALYZE_DIFF, denied, 1);

        assertEquals(2, auditLog.eventsForRun("run-1").size());
        assertTrue(auditLog.eventsForRun("run-1").getFirst().allowed());
        assertFalse(auditLog.eventsForRun("run-1").getLast().allowed());
        assertEquals("tool is not allowed for agent profile", auditLog.eventsForRun("run-1").getLast().reason());
        assertEquals(AgentType.VARIABLE_TRACE, auditLog.eventsForRun("run-1").getLast().agentType());
    }

    @Test
    void concreteAgentsBindProfilesAndRequireEvidenceAnswers() {
        AgentProfileRegistry profiles = AgentProfileRegistry.defaultProfiles();
        CodeAtlasAgent impact = new ImpactAnalysisAgent(profiles);
        CodeAtlasAgent variable = new VariableTraceAgent(profiles);
        CodeAtlasAgent question = new CodeQuestionAgent(profiles);

        assertEquals(AgentType.IMPACT_ANALYSIS, impact.type());
        assertEquals(AgentType.VARIABLE_TRACE, variable.type());
        assertEquals(AgentType.CODE_QUESTION, question.type());
        assertTrue(impact.profile().allowedTools().contains(AgentToolName.IMPACT_ANALYZE_DIFF));
        assertTrue(variable.profile().allowedTools().contains(AgentToolName.VARIABLE_TRACE));
        assertTrue(variable.profile().allowedTools().contains(AgentToolName.VARIABLE_TRACE_SOURCE));
        assertTrue(question.profile().allowedTools().contains(AgentToolName.RAG_SEMANTIC_SEARCH));

        assertThrows(IllegalArgumentException.class, () -> impact.requireValidAnswer(new AgentAnswer(
            AgentType.IMPACT_ANALYSIS,
            "No evidence should fail.",
            List.of(),
            List.of(),
            false
        )));
        AgentAnswer answer = new AgentAnswer(
            AgentType.IMPACT_ANALYSIS,
            "Impact path is evidence backed.",
            List.of("Route reaches service."),
            List.of(new AgentEvidenceRef("SPOON", "LIKELY", "e1", "method://x", "CALLS")),
            false
        );
        assertEquals(answer, impact.requireValidAnswer(answer));
    }

    @Test
    void answerContractCarriesEvidenceRefs() {
        AgentAnswer answer = new AgentAnswer(
            AgentType.CODE_QUESTION,
            "User save flows through Struts action and service.",
            List.of("Action routes to service method."),
            List.of(new AgentEvidenceRef("SPOON", "CERTAIN", "e1", "method://save", "CALLS")),
            false
        );

        assertTrue(answer.hasEvidence());
        assertEquals("SPOON", answer.evidence().getFirst().sourceType());
        assertEquals("CERTAIN", answer.evidence().getFirst().confidence());
    }
}
