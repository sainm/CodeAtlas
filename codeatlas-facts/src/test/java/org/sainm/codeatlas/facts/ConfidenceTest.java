package org.sainm.codeatlas.facts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class ConfidenceTest {
    @Test
    void certaintyOrderIsCertainLikelyPossibleUnknown() {
        assertEquals(0, Confidence.CERTAIN.ordinal());
        assertEquals(1, Confidence.LIKELY.ordinal());
        assertEquals(2, Confidence.POSSIBLE.ordinal());
        assertEquals(3, Confidence.UNKNOWN.ordinal());
    }

    @Test
    void pickHigherPrefersHigherConfidence() {
        assertEquals(Confidence.CERTAIN, Confidence.CERTAIN.pickHigher(Confidence.LIKELY));
        assertEquals(Confidence.CERTAIN, Confidence.LIKELY.pickHigher(Confidence.CERTAIN));
        assertEquals(Confidence.LIKELY, Confidence.LIKELY.pickHigher(Confidence.POSSIBLE));
        assertEquals(Confidence.LIKELY, Confidence.POSSIBLE.pickHigher(Confidence.LIKELY));
        assertEquals(Confidence.LIKELY, Confidence.UNKNOWN.pickHigher(Confidence.LIKELY));
    }

    @Test
    void pickHigherHandlesNull() {
        assertEquals(Confidence.CERTAIN, Confidence.CERTAIN.pickHigher((Confidence) null));
        assertEquals(Confidence.UNKNOWN, Confidence.UNKNOWN.pickHigher((Confidence) null));
    }

    @Test
    void batchAggregateReturnsBestConfidence() {
        assertEquals(Confidence.CERTAIN, Confidence.aggregate(List.of(Confidence.LIKELY, Confidence.POSSIBLE, Confidence.CERTAIN)));
        assertEquals(Confidence.LIKELY, Confidence.aggregate(List.of(Confidence.POSSIBLE, Confidence.LIKELY, Confidence.UNKNOWN)));
        assertEquals(Confidence.POSSIBLE, Confidence.aggregate(List.of(Confidence.UNKNOWN, Confidence.POSSIBLE)));
        assertEquals(Confidence.UNKNOWN, Confidence.aggregate(List.of(Confidence.UNKNOWN)));
    }

    @Test
    void batchAggregateEmptyDefaultsToUnknown() {
        assertEquals(Confidence.UNKNOWN, Confidence.aggregate(List.of()));
        assertEquals(Confidence.UNKNOWN, Confidence.aggregate(null));
    }
}
