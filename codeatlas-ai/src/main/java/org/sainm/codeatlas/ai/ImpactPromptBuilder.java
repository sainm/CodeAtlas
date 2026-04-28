package org.sainm.codeatlas.ai;

import org.sainm.codeatlas.graph.impact.ImpactEvidence;
import org.sainm.codeatlas.graph.impact.ImpactPath;
import org.sainm.codeatlas.graph.impact.ImpactPathStep;
import org.sainm.codeatlas.graph.impact.ImpactReport;
import java.util.stream.Collectors;

public final class ImpactPromptBuilder {
    private static final String SYSTEM = """
        You explain CodeAtlas static analysis results.
        Treat graph facts as the source of truth.
        Do not invent call edges, variables, JSP links, SQL tables, or risks.
        Every conclusion must cite an evidence path or say evidence is missing.
        """;

    private final SourceRedactor redactor;

    public ImpactPromptBuilder(SourceRedactor redactor) {
        this.redactor = redactor;
    }

    public AiPrompt buildSummaryPrompt(ImpactReport report, AiProjectPolicy policy) {
        return prompt("impact-summary", "Summarize the impact report and likely test focus.", report, policy);
    }

    public AiPrompt buildRiskPrompt(ImpactReport report, AiProjectPolicy policy) {
        return prompt("risk-explanation", "Explain why these paths are risky, grouped by evidence strength.", report, policy);
    }

    public AiPrompt buildTestSuggestionPrompt(ImpactReport report, AiProjectPolicy policy) {
        return prompt("test-suggestion", "Suggest focused regression tests based only on impacted paths.", report, policy);
    }

    public AiPrompt buildCallPathPrompt(ImpactReport report, AiProjectPolicy policy) {
        return prompt("call-path-explanation", "Explain each call path in business-readable language.", report, policy);
    }

    private AiPrompt prompt(String task, String instruction, ImpactReport report, AiProjectPolicy policy) {
        String evidence = report.evidenceList().stream()
            .map(item -> formatEvidence(item, policy))
            .collect(Collectors.joining("\n"));
        String paths = report.paths().stream()
            .map(this::formatPath)
            .collect(Collectors.joining("\n"));
        String user = """
            Instruction:
            %s

            Report:
            reportId=%s
            projectId=%s
            snapshotId=%s
            changeSetId=%s
            depth=%s
            truncated=%s

            Paths:
            %s

            Evidence:
            %s
            """.formatted(
            instruction,
            report.reportId(),
            report.projectId(),
            report.snapshotId(),
            report.changeSetId(),
            report.depth(),
            report.truncated(),
            paths.isBlank() ? "(none)" : paths,
            evidence.isBlank() ? "(none)" : evidence
        );
        return new AiPrompt(task, SYSTEM, user);
    }

    private String formatPath(ImpactPath path) {
        String steps = path.steps().stream()
            .map(this::formatStep)
            .collect(Collectors.joining(" -> "));
        return "- confidence=%s risk=%s changed=%s entry=%s path=%s reason=%s".formatted(
            path.confidence(),
            path.riskLevel(),
            path.changedSymbol().value(),
            path.entrypoint().value(),
            steps,
            path.reason()
        );
    }

    private String formatStep(ImpactPathStep step) {
        String relation = step.incomingRelation() == null ? "ENTRY" : step.incomingRelation().name();
        return "%s[%s,%s,%s]".formatted(step.symbolId().value(), relation, step.confidence(), step.sourceType());
    }

    private String formatEvidence(ImpactEvidence evidence, AiProjectPolicy policy) {
        String snippet = policy.enabled() && policy.allowSourceSnippets()
            ? redactor.redactAndTrim(evidence.snippet(), policy.maxSnippetCharacters())
            : "[SOURCE_SNIPPET_DISABLED]";
        return "- %s:%d type=%s source=%s confidence=%s snippet=%s".formatted(
            evidence.filePath(),
            evidence.lineNumber(),
            evidence.evidenceType(),
            evidence.sourceType(),
            evidence.confidence(),
            snippet
        );
    }
}
