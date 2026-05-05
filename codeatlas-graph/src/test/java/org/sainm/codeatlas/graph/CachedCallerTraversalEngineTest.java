package org.sainm.codeatlas.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sainm.codeatlas.facts.Confidence;
import org.sainm.codeatlas.facts.CurrentFactReport;
import org.sainm.codeatlas.facts.FactRecord;
import org.sainm.codeatlas.facts.SourceType;

class CachedCallerTraversalEngineTest {
    @Test
    void usesCacheWhenPresentAndFallsBackToActiveFactsWhenMissing() {
        String caller = method("com.acme.UserService", "load", "()V");
        String callee = method("com.acme.UserRepository", "find", "()V");
        CurrentFactReport report = CurrentFactReport.from("shop", List.of(call(caller, callee)));
        InMemoryAdjacencyCacheStore store = new InMemoryAdjacencyCacheStore();
        CachedCallerTraversalEngine engine = new CachedCallerTraversalEngine(store);

        CallerTraversalResult fallback = engine.findCallers(report, "snapshot-1", callee, 2, 10);
        store.rebuildPrimitiveCallCache(report, "snapshot-1");
        CallerTraversalResult cached = engine.findCallers(report, "snapshot-1", callee, 2, 10);
        store.invalidateProjectSnapshot("shop", "snapshot-1");
        CallerTraversalResult afterInvalidation = engine.findCallers(report, "snapshot-1", callee, 2, 10);

        assertEquals(fallback.callerPaths(), cached.callerPaths());
        assertEquals(fallback.callerPaths(), afterInvalidation.callerPaths());
        assertTrue(cached.callerPaths().stream()
                .anyMatch(path -> path.identityIds().equals(List.of(callee, caller))));
    }

    private static FactRecord call(String source, String target) {
        return FactRecord.create(
                List.of("src/main/java"),
                source,
                target,
                "CALLS",
                "direct",
                "shop",
                "snapshot-1",
                "analysis-1",
                "scope-1",
                "spoon",
                "src/main/java",
                "evidence-1",
                Confidence.CERTAIN,
                100,
                SourceType.SPOON);
    }

    private static String method(String owner, String method, String signature) {
        return "method://shop/_root/src/main/java/" + owner + "#" + method + signature;
    }
}
