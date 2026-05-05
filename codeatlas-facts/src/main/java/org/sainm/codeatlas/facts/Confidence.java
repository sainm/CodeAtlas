package org.sainm.codeatlas.facts;

import java.util.Collection;

public enum Confidence {
    CERTAIN,
    LIKELY,
    POSSIBLE,
    UNKNOWN;

    public Confidence pickHigher(Confidence other) {
        if (other == null) {
            return this;
        }
        return ordinal() <= other.ordinal() ? this : other;
    }

    public static Confidence aggregate(Collection<Confidence> values) {
        if (values == null || values.isEmpty()) {
            return UNKNOWN;
        }
        Confidence best = UNKNOWN;
        for (Confidence value : values) {
            best = best.pickHigher(value);
            if (best == CERTAIN) {
                break;
            }
        }
        return best;
    }
}
