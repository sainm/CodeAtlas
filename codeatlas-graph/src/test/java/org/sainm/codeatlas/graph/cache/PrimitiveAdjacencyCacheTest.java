package org.sainm.codeatlas.graph.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class PrimitiveAdjacencyCacheTest {
    @Test
    void buildsCallerCalleeAndBfsCache() {
        SymbolId a = method("A", "a");
        SymbolId b = method("B", "b");
        SymbolId c = method("C", "c");
        InMemoryGraphRepository repository = new InMemoryGraphRepository();
        repository.upsertFact(active(a, b));
        repository.upsertFact(active(b, c));

        PrimitiveAdjacencyCache cache = PrimitiveAdjacencyCache.build(
            new AdjacencyCacheKey("shop", "s1", RelationGroup.CALL),
            repository.activeFacts("shop", "s1")
        );

        assertEquals(List.of(b.value()), cache.callees(a));
        assertEquals(List.of(b.value()), cache.callers(c));
        assertEquals(List.of(a.value(), b.value()), cache.bfs(a, 2, 10).getFirst());
        assertEquals(3, cache.stats().nodeCount());
        assertEquals(2, cache.stats().edgeCount());
        assertTrue(cache.stats().hitRatio() > 0);
    }

    @Test
    void managerInvalidatesByProjectAndSnapshot() {
        AdjacencyCacheManager manager = new AdjacencyCacheManager();
        manager.getOrBuild(new AdjacencyCacheKey("shop", "s1", RelationGroup.CALL), List.of());
        manager.getOrBuild(new AdjacencyCacheKey("shop", "s2", RelationGroup.CALL), List.of());

        manager.invalidate("shop", "s1");

        assertEquals(1, manager.cacheCount());
    }

    private GraphFact active(SymbolId source, SymbolId target) {
        return GraphFact.active(
            new FactKey(source, RelationType.CALLS, target, "direct"),
            new EvidenceKey(SourceType.SPOON, "test", "A.java", 1, 1, "call"),
            "shop",
            "s1",
            "r1",
            "scope",
            Confidence.CERTAIN,
            SourceType.SPOON
        );
    }

    private SymbolId method(String owner, String name) {
        return SymbolId.method("shop", "_root", "src/main/java", "com.acme." + owner, name, "()V");
    }
}
