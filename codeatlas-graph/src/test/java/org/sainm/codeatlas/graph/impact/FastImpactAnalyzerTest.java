package org.sainm.codeatlas.graph.impact;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.EvidenceKey;
import org.sainm.codeatlas.graph.model.FactKey;
import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.store.InMemoryGraphRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class FastImpactAnalyzerTest {
    @Test
    void producesFastImpactReportFromChangedSymbol() {
        SymbolId action = method("com.acme.UserAction", "execute");
        SymbolId service = method("com.acme.UserService", "save");
        SymbolId mapper = method("com.acme.UserMapper", "insert");
        InMemoryGraphRepository repository = new InMemoryGraphRepository();
        repository.upsertFact(active(action, service, "UserAction.java", 10, Confidence.CERTAIN));
        repository.upsertFact(active(service, mapper, "UserService.java", 20, Confidence.LIKELY));

        ImpactReport report = new FastImpactAnalyzer().analyze(
            "r1",
            "shop",
            "snapshot-1",
            "change-1",
            repository.activeFacts("shop", "snapshot-1"),
            List.of(mapper),
            symbol -> symbol.equals(action),
            4,
            10
        );

        assertEquals(ReportDepth.FAST, report.depth());
        assertEquals(1, report.paths().size());
        assertEquals(2, report.evidenceList().size());
        assertEquals(Confidence.LIKELY, report.paths().getFirst().confidence());
    }

    private GraphFact active(SymbolId source, SymbolId target, String file, int line, Confidence confidence) {
        return GraphFact.active(
            new FactKey(source, RelationType.CALLS, target, "direct"),
            new EvidenceKey(SourceType.SPOON, "test", file, line, line, "call"),
            "shop",
            "snapshot-1",
            "run-1",
            file,
            confidence,
            SourceType.SPOON
        );
    }

    private SymbolId method(String owner, String name) {
        return SymbolId.method("shop", "_root", "src/main/java", owner, name, "()V");
    }
}
