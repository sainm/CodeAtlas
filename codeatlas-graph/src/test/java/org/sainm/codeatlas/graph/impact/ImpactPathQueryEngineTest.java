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

class ImpactPathQueryEngineTest {
    private final SymbolId action = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserAction", "execute", "()V");
    private final SymbolId service = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserService", "update", "()V");
    private final SymbolId mapper = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserMapper", "update", "()V");

    @Test
    void findsUpstreamEntrypointPathForChangedSymbol() {
        InMemoryGraphRepository repository = new InMemoryGraphRepository();
        repository.upsertFact(active(new FactKey(action, RelationType.CALLS, service, "direct"), "UserAction.java", 12, Confidence.CERTAIN));
        repository.upsertFact(active(new FactKey(service, RelationType.CALLS, mapper, "direct"), "UserService.java", 18, Confidence.LIKELY));

        List<ImpactPath> paths = new ImpactPathQueryEngine().findUpstreamImpactPaths(
            repository.activeFacts("shop", "snapshot-1"),
            mapper,
            symbol -> symbol.equals(action),
            4,
            10
        );

        assertEquals(1, paths.size());
        ImpactPath path = paths.getFirst();
        assertEquals(action, path.entrypoint());
        assertEquals(mapper, path.changedSymbol());
        assertEquals(3, path.steps().size());
        assertEquals(Confidence.LIKELY, path.confidence());
    }

    @Test
    void honorsDepthLimit() {
        InMemoryGraphRepository repository = new InMemoryGraphRepository();
        repository.upsertFact(active(new FactKey(action, RelationType.CALLS, service, "direct"), "UserAction.java", 12, Confidence.CERTAIN));
        repository.upsertFact(active(new FactKey(service, RelationType.CALLS, mapper, "direct"), "UserService.java", 18, Confidence.CERTAIN));

        List<ImpactPath> paths = new ImpactPathQueryEngine().findUpstreamImpactPaths(
            repository.activeFacts("shop", "snapshot-1"),
            mapper,
            symbol -> symbol.equals(action),
            1,
            10
        );

        assertEquals(0, paths.size());
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
