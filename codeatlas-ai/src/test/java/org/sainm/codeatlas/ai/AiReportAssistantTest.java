package org.sainm.codeatlas.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.graph.impact.ImpactEvidence;
import org.sainm.codeatlas.graph.impact.ImpactPath;
import org.sainm.codeatlas.graph.impact.ImpactPathStep;
import org.sainm.codeatlas.graph.impact.ImpactReport;
import org.sainm.codeatlas.graph.impact.ReportDepth;
import org.sainm.codeatlas.graph.impact.RiskLevel;
import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiReportAssistantTest {
    @Test
    void fallsBackToStaticSummaryWhenAiIsDisabled() {
        AiReportAssistant assistant = new AiReportAssistant(
            (prompt, config) -> AiTextResult.failure("should not be called"),
            new ImpactPromptBuilder(new SourceRedactor())
        );

        AiTextResult result = assistant.summarizeImpact(emptyReport(), AiRuntimeConfig.disabled(), AiProjectPolicy.disabled());

        assertTrue(result.success());
        assertEquals("Static impact report: paths=0, evidence=0, truncated=false", result.text());
    }

    @Test
    void buildsStaticRiskAndTestSuggestionsWhenAiIsDisabled() {
        AiReportAssistant assistant = new AiReportAssistant(
            (prompt, config) -> AiTextResult.failure("should not be called"),
            new ImpactPromptBuilder(new SourceRedactor())
        );

        ImpactAssistantResult result = assistant.analyzeImpact(
            pathReport(),
            AiRuntimeConfig.disabled(),
            AiProjectPolicy.disabled()
        );

        assertTrue(!result.aiAssisted());
        assertTrue(result.summary().contains("paths=1"));
        assertTrue(result.riskExplanation().contains("Highest static risk is HIGH"));
        assertTrue(result.testSuggestions().stream().anyMatch(text -> text.contains("web entrypoints")));
        assertTrue(result.testSuggestions().stream().anyMatch(text -> text.contains("DAO/Mapper")));
        assertEquals(1, result.evidenceCount());
    }

    @Test
    void usesProviderTextWhenAiSucceeds() {
        AiReportAssistant assistant = new AiReportAssistant(
            (prompt, config) -> switch (prompt.task()) {
                case "impact-summary" -> AiTextResult.success("AI summary");
                case "risk-explanation" -> AiTextResult.success("AI risk");
                case "test-suggestion" -> AiTextResult.success("- Test route\n- Test mapper");
                default -> AiTextResult.failure("unexpected");
            },
            new ImpactPromptBuilder(new SourceRedactor())
        );

        ImpactAssistantResult result = assistant.analyzeImpact(
            pathReport(),
            new AiRuntimeConfig(AiProviderType.CUSTOM, "http://localhost", "k", "model", "embed", 3),
            new AiProjectPolicy(true, true, 100)
        );

        assertTrue(result.aiAssisted());
        assertEquals("AI summary", result.summary());
        assertEquals("AI risk", result.riskExplanation());
        assertEquals(List.of("Test route", "Test mapper"), result.testSuggestions());
    }

    @Test
    void auditLogRedactsAiPromptAndDoesNotStoreApiKey() {
        AiRequestAuditLog auditLog = new AiRequestAuditLog(new SourceRedactor());
        AiReportAssistant assistant = new AiReportAssistant(
            (prompt, config) -> AiTextResult.success("ok"),
            new ImpactPromptBuilder(new SourceRedactor()),
            auditLog
        );

        assistant.summarizeImpact(
            secretReport(),
            new AiRuntimeConfig(AiProviderType.CUSTOM, "http://localhost", "sk-secret-api-key", "model", "embed", 3),
            new AiProjectPolicy(true, true, 200)
        );

        assertEquals(1, auditLog.events().size());
        AiRequestAuditEvent event = auditLog.events().getFirst();
        assertEquals("impact-summary", event.task());
        assertEquals("model", event.model());
        assertTrue(!event.redactedUserPrompt().contains("plain-secret"));
        assertTrue(!event.toString().contains("sk-secret-api-key"));
        assertTrue(event.redactedUserPrompt().contains("password=[REDACTED]"));
    }

    @Test
    void fallsBackToStaticSummaryWhenProviderFails() {
        AiProvider provider = new AiProvider() {
            @Override
            public AiProviderType type() {
                return AiProviderType.CUSTOM;
            }

            @Override
            public AiTextResult complete(AiPrompt prompt, AiRuntimeConfig config) {
                return AiTextResult.failure("timeout");
            }
        };
        AiReportAssistant assistant = new AiReportAssistant(provider, new ImpactPromptBuilder(new SourceRedactor()));

        AiTextResult result = assistant.summarizeImpact(
            emptyReport(),
            new AiRuntimeConfig(AiProviderType.CUSTOM, "http://localhost", "k", "model", "embed", 3),
            new AiProjectPolicy(true, true, 100)
        );

        assertTrue(result.success());
        assertEquals("Static impact report: paths=0, evidence=0, truncated=false", result.text());
    }

    private ImpactReport emptyReport() {
        return new ImpactReport("r1", "p1", "s1", "c1", ReportDepth.FAST, null, List.of(), List.of(), false);
    }

    private ImpactReport pathReport() {
        SymbolId action = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserAction", "execute", "()V");
        SymbolId service = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserService", "save", "()V");
        SymbolId table = SymbolId.logicalPath(org.sainm.codeatlas.graph.model.SymbolKind.DB_TABLE, "shop", "_root", "db", "users", null);
        ImpactPath path = ImpactPath.fromSteps(
            action,
            table,
            List.of(
                new ImpactPathStep(action, null, SourceType.SPOON, Confidence.CERTAIN),
                new ImpactPathStep(service, RelationType.ROUTES_TO, SourceType.STRUTS_CONFIG, Confidence.CERTAIN),
                new ImpactPathStep(table, RelationType.WRITES_TABLE, SourceType.SQL, Confidence.LIKELY)
            ),
            RiskLevel.HIGH,
            "route reaches table",
            false
        );
        ImpactEvidence evidence = new ImpactEvidence("UserAction.java", 12, "call", "service.save()", SourceType.SPOON, Confidence.CERTAIN);
        return new ImpactReport("r2", "p1", "s1", "c1", ReportDepth.FAST, null, List.of(path), List.of(evidence), false);
    }

    private ImpactReport secretReport() {
        ImpactEvidence evidence = new ImpactEvidence(
            "config.properties",
            1,
            "snippet",
            "password=plain-secret",
            SourceType.SPOON,
            Confidence.CERTAIN
        );
        return new ImpactReport("r3", "p1", "s1", "c1", ReportDepth.FAST, null, List.of(), List.of(evidence), false);
    }
}
