package org.sainm.codeatlas.graph.impact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;

class DeepImpactReportSupplementerTest {
    @Test
    void createsDeepReportByMergingSupplementalPathsAndEvidence() {
        SymbolId action = method("UserAction", "execute");
        SymbolId service = method("UserService", "save");
        SymbolId dao = method("UserDao", "insert");
        ImpactPath fastPath = path(action, service, false);
        ImpactPath deepPath = path(action, dao, true);
        ImpactEvidence fastEvidence = evidence("UserAction.java", 12, "call");
        ImpactEvidence deepEvidence = evidence("UserDao.java", 30, "jdbc");
        ImpactReport fast = new ImpactReport(
            "r1",
            "shop",
            "s1",
            "c1",
            ReportDepth.FAST,
            Instant.parse("2026-05-01T00:00:00Z"),
            List.of(fastPath),
            List.of(fastEvidence),
            false
        );

        ImpactReport deep = new DeepImpactReportSupplementer().supplement(
            fast,
            List.of(fastPath, deepPath),
            List.of(fastEvidence, deepEvidence)
        );

        assertEquals(ReportDepth.DEEP, deep.depth());
        assertEquals("r1", deep.reportId());
        assertEquals(2, deep.paths().size());
        assertEquals(2, deep.evidenceList().size());
        assertTrue(deep.truncated());
        assertTrue(deep.paths().contains(deepPath));
        assertTrue(deep.evidenceList().contains(deepEvidence));
    }

    private ImpactPath path(SymbolId entrypoint, SymbolId target, boolean truncated) {
        return ImpactPath.fromSteps(
            entrypoint,
            target,
            List.of(
                new ImpactPathStep(entrypoint, null, SourceType.SPOON, Confidence.CERTAIN),
                new ImpactPathStep(target, RelationType.CALLS, SourceType.SPOON, Confidence.LIKELY)
            ),
            RiskLevel.MEDIUM,
            entrypoint.memberName() + " reaches " + target.memberName(),
            truncated
        );
    }

    private ImpactEvidence evidence(String filePath, int line, String type) {
        return new ImpactEvidence(filePath, line, type, "", SourceType.SPOON, Confidence.LIKELY);
    }

    private SymbolId method(String owner, String name) {
        return SymbolId.method("shop", "_root", "src/main/java", "com.acme." + owner, name, "()V");
    }
}
