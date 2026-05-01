package org.sainm.codeatlas.graph.impact;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;

class ImpactReportDeepAnalysisSourceTest {
    @Test
    void jsonMarksTaiEPathStepsAsDeepSupplement() {
        ImpactReport report = reportWithTaiEStep();

        String json = new ImpactReportJsonExporter().export(report);

        assertTrue(json.contains("\"analysisLayer\": \"DEEP_SUPPLEMENT\""));
        assertTrue(json.contains("\"sourceType\": \"TAI_E\""));
    }

    @Test
    void markdownMarksTaiEPathStepsAsDeepSupplement() {
        ImpactReport report = reportWithTaiEStep();

        String markdown = new ImpactReportMarkdownExporter().export(report);

        assertTrue(markdown.contains("DEEP_SUPPLEMENT"));
        assertTrue(markdown.contains("TAI_E"));
    }

    private ImpactReport reportWithTaiEStep() {
        SymbolId action = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserAction", "execute", "()V");
        SymbolId service = SymbolId.method("shop", "_root", "bytecode", "com.acme.UserService", "save", "()V");
        ImpactPath path = new ImpactPath(
            action,
            service,
            List.of(
                new ImpactPathStep(action, null, SourceType.SPOON, Confidence.CERTAIN),
                new ImpactPathStep(service, RelationType.CALLS, SourceType.TAI_E, Confidence.LIKELY)
            ),
            Confidence.LIKELY,
            Set.of(SourceType.SPOON, SourceType.TAI_E),
            RiskLevel.MEDIUM,
            "Tai-e supplemented indirect call",
            false
        );
        return new ImpactReport(
            "r1",
            "shop",
            "s1",
            "c1",
            ReportDepth.DEEP,
            Instant.parse("2026-05-01T00:00:00Z"),
            List.of(path),
            List.of(),
            false
        );
    }
}
