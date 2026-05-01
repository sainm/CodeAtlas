package org.sainm.codeatlas.ai;

import org.sainm.codeatlas.graph.impact.ImpactPath;
import org.sainm.codeatlas.graph.impact.ImpactReport;
import org.sainm.codeatlas.graph.impact.RiskLevel;
import org.sainm.codeatlas.graph.model.RelationType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AiReportAssistant {
    private final AiProvider provider;
    private final ImpactPromptBuilder promptBuilder;
    private final AiRequestAuditLog auditLog;

    public AiReportAssistant(AiProvider provider, ImpactPromptBuilder promptBuilder) {
        this(provider, promptBuilder, new AiRequestAuditLog(new SourceRedactor()));
    }

    public AiReportAssistant(AiProvider provider, ImpactPromptBuilder promptBuilder, AiRequestAuditLog auditLog) {
        this.provider = provider;
        this.promptBuilder = promptBuilder;
        this.auditLog = auditLog == null ? new AiRequestAuditLog(new SourceRedactor()) : auditLog;
    }

    public AiTextResult summarizeImpact(ImpactReport report, AiRuntimeConfig runtimeConfig, AiProjectPolicy policy) {
        if (!runtimeConfig.enabled() || !policy.enabled()) {
            return AiTextResult.success(staticFallback(report));
        }
        AiTextResult result = complete(promptBuilder.buildSummaryPrompt(report, policy), runtimeConfig);
        return result.success() ? result : AiTextResult.success(staticFallback(report));
    }

    public ImpactAssistantResult analyzeImpact(ImpactReport report, AiRuntimeConfig runtimeConfig, AiProjectPolicy policy) {
        boolean aiEnabled = runtimeConfig.enabled() && policy.enabled();
        AiTextResult summary = aiEnabled
            ? complete(promptBuilder.buildSummaryPrompt(report, policy), runtimeConfig)
            : AiTextResult.failure("disabled");
        AiTextResult risk = aiEnabled
            ? complete(promptBuilder.buildRiskPrompt(report, policy), runtimeConfig)
            : AiTextResult.failure("disabled");
        AiTextResult tests = aiEnabled
            ? complete(promptBuilder.buildTestSuggestionPrompt(report, policy), runtimeConfig)
            : AiTextResult.failure("disabled");

        return new ImpactAssistantResult(
            summary.success() ? summary.text() : staticFallback(report),
            risk.success() ? risk.text() : staticRiskExplanation(report),
            tests.success() ? suggestionsFromText(tests.text()) : staticTestSuggestions(report),
            report.evidenceList().size(),
            summary.success() || risk.success() || tests.success()
        );
    }

    private String staticFallback(ImpactReport report) {
        return "Static impact report: paths=%d, evidence=%d, truncated=%s"
            .formatted(report.paths().size(), report.evidenceList().size(), report.truncated());
    }

    private AiTextResult complete(AiPrompt prompt, AiRuntimeConfig runtimeConfig) {
        AiTextResult result = provider.complete(prompt, runtimeConfig);
        auditLog.record(prompt, runtimeConfig, result);
        return result;
    }

    private String staticRiskExplanation(ImpactReport report) {
        if (report.paths().isEmpty()) {
            return "No active impact paths were found from the changed symbol set.";
        }
        RiskLevel highestRisk = report.paths().stream()
            .map(ImpactPath::riskLevel)
            .min(Comparator.comparingInt(this::riskRank))
            .orElse(RiskLevel.LOW);
        long certainOrLikely = report.paths().stream()
            .flatMap(path -> path.steps().stream())
            .filter(step -> step.incomingRelation() != null)
            .filter(step -> step.confidence().rank() >= org.sainm.codeatlas.graph.model.Confidence.LIKELY.rank())
            .count();
        return "Highest static risk is %s across %d path(s); %d relation step(s) have likely or certain evidence."
            .formatted(highestRisk, report.paths().size(), certainOrLikely);
    }

    private List<String> staticTestSuggestions(ImpactReport report) {
        List<String> suggestions = new ArrayList<>();
        if (report.paths().isEmpty()) {
            suggestions.add("Run focused smoke tests around the changed files because no graph entrypoint path was found.");
            return suggestions;
        }
        if (hasRelation(report, RelationType.SUBMITS_TO) || hasRelation(report, RelationType.ROUTES_TO)) {
            suggestions.add("Exercise affected web entrypoints, Struts actions, and controller routes from the UI or API layer.");
        }
        if (hasRelation(report, RelationType.READS_PARAM)
            || hasRelation(report, RelationType.WRITES_PARAM)
            || hasRelation(report, RelationType.PASSES_PARAM)
            || hasRelation(report, RelationType.BINDS_TO)) {
            suggestions.add("Add request-parameter regression cases for required, missing, and boundary values.");
        }
        if (hasRelation(report, RelationType.READS_TABLE) || hasRelation(report, RelationType.WRITES_TABLE)) {
            suggestions.add("Run DAO/Mapper integration tests and verify impacted table read/write behavior.");
        }
        if (hasRelation(report, RelationType.INCLUDES) || hasRelation(report, RelationType.FORWARDS_TO)) {
            suggestions.add("Check affected JSP include and forward paths for rendering and navigation regressions.");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("Run unit tests around the changed symbol and its direct callers.");
        }
        return suggestions;
    }

    private boolean hasRelation(ImpactReport report, RelationType relationType) {
        return report.paths().stream()
            .flatMap(path -> path.steps().stream())
            .anyMatch(step -> step.incomingRelation() == relationType);
    }

    private List<String> suggestionsFromText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return text.lines()
            .map(line -> line.replaceFirst("^[-*\\d.\\s]+", "").trim())
            .filter(line -> !line.isBlank())
            .toList();
    }

    private int riskRank(RiskLevel riskLevel) {
        return switch (riskLevel) {
            case HIGH -> 0;
            case MEDIUM -> 1;
            case LOW -> 2;
            case UNKNOWN -> 3;
        };
    }
}
