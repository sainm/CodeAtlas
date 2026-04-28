package org.sainm.codeatlas.graph.impact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImpactReportTest {
    @Test
    void pathConfidenceIsBoundedByWeakestEvidenceEdge() {
        SymbolId controller = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserController", "save", "()V");
        SymbolId service = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserService", "save", "()V");
        SymbolId mapper = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserMapper", "update", "()V");

        ImpactPath path = ImpactPath.fromSteps(
            controller,
            mapper,
            List.of(
                new ImpactPathStep(controller, null, SourceType.SPOON, Confidence.CERTAIN),
                new ImpactPathStep(service, RelationType.CALLS, SourceType.SPOON, Confidence.CERTAIN),
                new ImpactPathStep(mapper, RelationType.CALLS, SourceType.STATIC_RULE, Confidence.LIKELY)
            ),
            RiskLevel.MEDIUM,
            "Controller reaches changed mapper method.",
            false
        );

        assertEquals(Confidence.LIKELY, path.confidence());
        assertTrue(path.sourceTypes().contains(SourceType.SPOON));
        assertTrue(path.sourceTypes().contains(SourceType.STATIC_RULE));
    }

    @Test
    void reportKeepsStructuredPathsAndEvidence() {
        SymbolId entrypoint = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserAction", "execute", "()V");
        ImpactPath path = ImpactPath.fromSteps(
            entrypoint,
            entrypoint,
            List.of(new ImpactPathStep(entrypoint, null, SourceType.STRUTS_CONFIG, Confidence.CERTAIN)),
            RiskLevel.HIGH,
            "Changed action is an entrypoint.",
            false
        );
        ImpactEvidence evidence = new ImpactEvidence(
            "WEB-INF/struts-config.xml",
            42,
            "struts-action",
            "<action path=\"/user/save\" ...>",
            SourceType.STRUTS_CONFIG,
            Confidence.CERTAIN
        );

        ImpactReport report = new ImpactReport(
            "r1",
            "p1",
            "s1",
            "c1",
            ReportDepth.FAST,
            null,
            List.of(path),
            List.of(evidence),
            false
        );

        assertEquals(1, report.paths().size());
        assertEquals(1, report.evidenceList().size());
        assertEquals(ReportDepth.FAST, report.depth());
    }
}
