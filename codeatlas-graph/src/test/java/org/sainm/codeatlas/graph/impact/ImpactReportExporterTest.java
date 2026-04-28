package org.sainm.codeatlas.graph.impact;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImpactReportExporterTest {
    @Test
    void exportsJsonReportShape() {
        String json = new ImpactReportJsonExporter().export(report());

        assertTrue(json.contains("\"reportId\": \"r1\""));
        assertTrue(json.contains("\"entrypoint\""));
        assertTrue(json.contains("\"evidenceList\""));
        assertTrue(json.contains("\\\"quoted\\\""));
    }

    @Test
    void exportsMarkdownReport() {
        String markdown = new ImpactReportMarkdownExporter().export(report());

        assertTrue(markdown.contains("# Impact Report"));
        assertTrue(markdown.contains("## Paths"));
        assertTrue(markdown.contains("## Evidence"));
        assertTrue(markdown.contains("Path 1"));
    }

    private ImpactReport report() {
        SymbolId action = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserAction", "execute", "()V");
        SymbolId mapper = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserMapper", "insert", "()V");
        ImpactPath path = ImpactPath.fromSteps(
            action,
            mapper,
            List.of(
                new ImpactPathStep(action, null, SourceType.SPOON, Confidence.CERTAIN),
                new ImpactPathStep(mapper, org.sainm.codeatlas.graph.model.RelationType.CALLS, SourceType.SPOON, Confidence.LIKELY)
            ),
            RiskLevel.MEDIUM,
            "Action reaches mapper \"quoted\".",
            false
        );
        ImpactEvidence evidence = new ImpactEvidence(
            "UserAction.java",
            12,
            "call",
            "mapper.insert(\"quoted\")",
            SourceType.SPOON,
            Confidence.LIKELY
        );
        return new ImpactReport(
            "r1",
            "shop",
            "snapshot-1",
            "change-1",
            ReportDepth.FAST,
            Instant.parse("2026-04-28T00:00:00Z"),
            List.of(path),
            List.of(evidence),
            false
        );
    }
}
