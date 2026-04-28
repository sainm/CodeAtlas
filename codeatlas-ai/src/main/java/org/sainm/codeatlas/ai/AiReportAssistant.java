package org.sainm.codeatlas.ai;

import org.sainm.codeatlas.graph.impact.ImpactReport;

public final class AiReportAssistant {
    private final AiProvider provider;
    private final ImpactPromptBuilder promptBuilder;

    public AiReportAssistant(AiProvider provider, ImpactPromptBuilder promptBuilder) {
        this.provider = provider;
        this.promptBuilder = promptBuilder;
    }

    public AiTextResult summarizeImpact(ImpactReport report, AiRuntimeConfig runtimeConfig, AiProjectPolicy policy) {
        if (!runtimeConfig.enabled() || !policy.enabled()) {
            return AiTextResult.success(staticFallback(report));
        }
        AiTextResult result = provider.complete(promptBuilder.buildSummaryPrompt(report, policy), runtimeConfig);
        return result.success() ? result : AiTextResult.success(staticFallback(report));
    }

    private String staticFallback(ImpactReport report) {
        return "Static impact report: paths=%d, evidence=%d, truncated=%s"
            .formatted(report.paths().size(), report.evidenceList().size(), report.truncated());
    }
}
