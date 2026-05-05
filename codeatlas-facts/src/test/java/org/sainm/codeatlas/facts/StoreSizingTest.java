package org.sainm.codeatlas.facts;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class StoreSizingTest {
    @Test
    void smallCountReturnsOk() {
        assertEquals(StoreSizing.Recommendation.OK,
                StoreSizing.evaluate(10_000));
        assertEquals(StoreSizing.Recommendation.OK,
                StoreSizing.evaluate(499_999));
    }

    @Test
    void nearMillionReturnsSwitchRecommended() {
        assertEquals(StoreSizing.Recommendation.SWITCH_RECOMMENDED,
                StoreSizing.evaluate(500_000));
        assertEquals(StoreSizing.Recommendation.SWITCH_RECOMMENDED,
                StoreSizing.evaluate(4_999_999));
    }

    @Test
    void exceedsHardLimitReturnsReject() {
        assertEquals(StoreSizing.Recommendation.REJECT,
                StoreSizing.evaluate(5_000_000));
    }

    @Test
    void smallInsertPassesGuard() {
        InMemoryFactStore store = InMemoryFactStore.defaults();
        assertDoesNotThrow(() -> store.insert(fact()));
    }

    @Test
    void rejectThresholdThrows() {
        assertThrows(IllegalStateException.class,
                () -> StoreSizing.guardInsert(InMemoryFactStore.defaults(), "shop", 5_000_001));
    }

    private static FactRecord fact() {
        return FactRecord.create(
                java.util.List.of("src/main/java"),
                "method://shop/_root/src/main/java/A#m()V",
                "method://shop/_root/src/main/java/B#n()V",
                "CALLS",
                "direct",
                "shop",
                "snap-1",
                "analysis-1",
                "scope-1",
                "spoon",
                "src/main/java",
                "ev-1",
                Confidence.CERTAIN,
                100,
                SourceType.SPOON);
    }
}
