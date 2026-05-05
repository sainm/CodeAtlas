package org.sainm.codeatlas.facts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class BatchOperationSupportTest {
    @Test
    void ordersFactsDeterministicallyForStableBatchProcessing() {
        FactRecord first = fact("z1");
        FactRecord second = fact("a2");
        FactRecord third = fact("m3");

        List<FactRecord> ordered = BatchOperationSupport.defaults().stableOrder(List.of(first, second, third));
        List<FactRecord> orderedAgain = BatchOperationSupport.defaults().stableOrder(List.of(third, first, second));

        assertEquals(ordered.get(0).factKey(), orderedAgain.get(0).factKey());
        assertEquals(ordered.get(1).factKey(), orderedAgain.get(1).factKey());
        assertEquals(ordered.get(2).factKey(), orderedAgain.get(2).factKey());
    }

    @Test
    void insertsBatchIntoStoreInStableOrder() {
        InMemoryFactStore store = InMemoryFactStore.defaults();
        BatchOperationSupport support = BatchOperationSupport.defaults();

        support.insertBatch(store, List.of(fact("c"), fact("a"), fact("b")));
        List<FactRecord> result = store.activeFacts("shop", "snap-1");

        assertEquals(3, result.size());
    }

    @Test
    void retriesOnTransientException() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        BatchOperationSupport support = BatchOperationSupport.of(3, Duration.ofMillis(1), Duration.ofMillis(10));
        AtomicInteger attempts = new AtomicInteger(0);

        support.withRetry(() -> {
            attempts.incrementAndGet();
            if (attempts.get() < 3) {
                throw new RuntimeException("deadlock detected");
            }
        });

        assertEquals(3, attempts.get());
    }

    @Test
    void throwsAfterMaxRetriesExceeded() {
        BatchOperationSupport support = BatchOperationSupport.of(2, Duration.ofMillis(1), Duration.ofMillis(10));
        AtomicInteger attempts = new AtomicInteger(0);

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                support.withRetry(() -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException("deadlock detected");
                }));

        assertEquals(3, attempts.get());
        assertTrue(exception.getMessage().contains("deadlock"));
    }

    private static FactRecord fact(String qualifier) {
        return FactRecord.create(
                List.of("src/main/java"),
                "method://shop/_root/src/main/java/A#m()V",
                "method://shop/_root/src/main/java/" + qualifier + "#n()V",
                "CALLS",
                qualifier,
                "shop",
                "snap-1",
                "analysis-1",
                "scope-1",
                "spoon",
                "src/main/java",
                "evidence-1",
                Confidence.CERTAIN,
                100,
                SourceType.SPOON);
    }
}
