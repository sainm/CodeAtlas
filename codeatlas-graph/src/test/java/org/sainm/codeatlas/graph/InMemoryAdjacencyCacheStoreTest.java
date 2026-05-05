package org.sainm.codeatlas.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sainm.codeatlas.facts.Confidence;
import org.sainm.codeatlas.facts.CurrentFactReport;
import org.sainm.codeatlas.facts.FactRecord;
import org.sainm.codeatlas.facts.SourceType;

class InMemoryAdjacencyCacheStoreTest {
    @Test
    void shardsPrimitiveCallCacheByProjectSnapshotAndRelationFamily() {
        String a = method("com.acme.A", "a", "()V");
        String b = method("com.acme.B", "b", "()V");
        CurrentFactReport report = CurrentFactReport.from("shop", List.of(call(a, b)));
        InMemoryAdjacencyCacheStore store = new InMemoryAdjacencyCacheStore();

        store.rebuildPrimitiveCallCache(report, "snapshot-1");

        assertTrue(store.primitiveCallCache("shop", "snapshot-1").isPresent());
        assertEquals(List.of(b), store.primitiveCallCache("shop", "snapshot-1").orElseThrow().callees(a));
        assertTrue(store.primitiveCallCache("shop", "snapshot-2").isEmpty());
        store.invalidateProjectSnapshot("shop", "snapshot-1");
        assertEquals(0, store.cacheCount());
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
