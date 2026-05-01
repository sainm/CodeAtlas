package org.sainm.codeatlas.graph.impact;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sainm.codeatlas.graph.benchmark.FfmActivationDecision;
import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.EvidenceKey;
import org.sainm.codeatlas.graph.model.FactKey;
import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.store.InMemoryGraphRepository;

class ImpactPathQueryRouterTest {
    private final SymbolId action = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserAction", "execute", "()V");
    private final SymbolId service = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserService", "update", "()V");
    private final SymbolId mapper = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserMapper", "update", "()V");

    @Test
    void usesDefaultQueryEngineWhenFfmIsNotRecommended() {
        ImpactPathQueryRouter router = new ImpactPathQueryRouter();

        List<ImpactPath> paths = router.findUpstreamImpactPaths(
            activeFacts(),
            mapper,
            symbol -> symbol.equals(action),
            4,
            10,
            new FfmActivationDecision(false, "keep heap path", List.of())
        );

        assertEquals(1, paths.size());
        assertEquals("Entrypoint reaches changed symbol through active graph facts.", paths.getFirst().reason());
    }

    @Test
    void usesFfmQueryEngineOnlyWhenBenchmarkRecommendsIt() {
        ImpactPathQueryRouter router = new ImpactPathQueryRouter();

        List<ImpactPath> paths = router.findUpstreamImpactPaths(
            activeFacts(),
            mapper,
            symbol -> symbol.equals(action),
            4,
            10,
            new FfmActivationDecision(true, "ffm recommended", List.of("large graph"))
        );

        assertEquals(1, paths.size());
        assertEquals(action, paths.getFirst().entrypoint());
        assertEquals(mapper, paths.getFirst().changedSymbol());
        assertEquals("Entrypoint reaches changed symbol through benchmark-enabled FFM graph index.", paths.getFirst().reason());
        assertEquals(RelationType.CALLS, paths.getFirst().steps().get(1).incomingRelation());
    }

    private List<org.sainm.codeatlas.graph.store.ActiveFact> activeFacts() {
        InMemoryGraphRepository repository = new InMemoryGraphRepository();
        repository.upsertFact(active(new FactKey(action, RelationType.CALLS, service, "direct"), "UserAction.java", 12, Confidence.CERTAIN));
        repository.upsertFact(active(new FactKey(service, RelationType.CALLS, mapper, "direct"), "UserService.java", 18, Confidence.LIKELY));
        return repository.activeFacts("shop", "snapshot-1");
    }

    private GraphFact active(FactKey factKey, String path, int line, Confidence confidence) {
        return GraphFact.active(
            factKey,
            new EvidenceKey(SourceType.SPOON, "test", path, line, line, "call"),
            "shop",
            "snapshot-1",
            "run-1",
            path,
            confidence,
            SourceType.SPOON
        );
    }
}
