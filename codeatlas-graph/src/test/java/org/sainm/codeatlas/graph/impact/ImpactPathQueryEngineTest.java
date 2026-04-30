package org.sainm.codeatlas.graph.impact;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.EvidenceKey;
import org.sainm.codeatlas.graph.model.FactKey;
import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.model.SymbolKind;
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
        assertEquals(1, path.steps().get(1).evidenceKeys().size());
        assertEquals("UserAction.java", path.steps().get(1).evidenceKeys().getFirst().path());
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

    @Test
    void treatsIncludedJspAsUpstreamImpactRelation() {
        SymbolId page = SymbolId.logicalPath(SymbolKind.JSP_PAGE, "shop", "_root", "src/main/webapp", "user/edit.jsp", null);
        SymbolId include = SymbolId.logicalPath(SymbolKind.JSP_PAGE, "shop", "_root", "src/main/webapp", "common/footer.jsp", null);
        InMemoryGraphRepository repository = new InMemoryGraphRepository();
        repository.upsertFact(active(new FactKey(page, RelationType.INCLUDES, include, "static_directive:/common/footer.jsp"), "edit.jsp", 1, Confidence.CERTAIN));

        List<ImpactPath> paths = new ImpactPathQueryEngine().findUpstreamImpactPaths(
            repository.activeFacts("shop", "snapshot-1"),
            include,
            symbol -> symbol.equals(page),
            2,
            10
        );

        assertEquals(1, paths.size());
        assertEquals(page, paths.getFirst().entrypoint());
        assertEquals(RelationType.INCLUDES, paths.getFirst().steps().get(1).incomingRelation());
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
