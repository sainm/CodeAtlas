package org.sainm.codeatlas.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.graph.impact.ImpactEvidence;
import org.sainm.codeatlas.graph.impact.ImpactPath;
import org.sainm.codeatlas.graph.impact.ImpactPathStep;
import org.sainm.codeatlas.graph.impact.ImpactReport;
import org.sainm.codeatlas.graph.impact.ReportDepth;
import org.sainm.codeatlas.graph.impact.RiskLevel;
import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImpactPromptBuilderTest {
    @Test
    void promptContainsEvidencePathAndRedactsSecrets() {
        ImpactPromptBuilder builder = new ImpactPromptBuilder(new SourceRedactor());
        AiPrompt prompt = builder.buildSummaryPrompt(report("apiKey=secret123"), new AiProjectPolicy(true, true, 200));

        assertTrue(prompt.system().contains("Treat graph facts as the source of truth"));
        assertTrue(prompt.user().contains("UserAction.java:12"));
        assertTrue(prompt.user().contains("apiKey=[REDACTED]"));
        assertFalse(prompt.user().contains("secret123"));
    }

    @Test
    void projectPolicyCanDisableSourceSnippets() {
        ImpactPromptBuilder builder = new ImpactPromptBuilder(new SourceRedactor());
        AiPrompt prompt = builder.buildRiskPrompt(report("password=abc"), new AiProjectPolicy(true, false, 200));

        assertTrue(prompt.user().contains("[SOURCE_SNIPPET_DISABLED]"));
        assertFalse(prompt.user().contains("password=abc"));
    }

    private ImpactReport report(String snippet) {
        SymbolId action = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserAction", "execute", "()V");
        ImpactPath path = ImpactPath.fromSteps(
            action,
            action,
            List.of(new ImpactPathStep(action, null, SourceType.SPOON, Confidence.CERTAIN)),
            RiskLevel.HIGH,
            "entrypoint changed",
            false
        );
        ImpactEvidence evidence = new ImpactEvidence("UserAction.java", 12, "method", snippet, SourceType.SPOON, Confidence.CERTAIN);
        return new ImpactReport("r1", "p1", "s1", "c1", ReportDepth.FAST, null, List.of(path), List.of(evidence), false);
    }
}
