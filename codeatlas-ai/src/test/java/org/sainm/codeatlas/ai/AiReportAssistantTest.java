package org.sainm.codeatlas.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.graph.impact.ImpactReport;
import org.sainm.codeatlas.graph.impact.ReportDepth;
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
}
