package org.sainm.codeatlas.graph.model;

public enum Confidence {
    UNKNOWN(0),
    POSSIBLE(1),
    LIKELY(2),
    CERTAIN(3);

    private final int rank;

    Confidence(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public static Confidence max(Confidence left, Confidence right) {
        return left.rank >= right.rank ? left : right;
    }
}

