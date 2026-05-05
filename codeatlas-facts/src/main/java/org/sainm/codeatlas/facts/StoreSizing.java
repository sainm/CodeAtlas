package org.sainm.codeatlas.facts;

/**
 * Detects when the in-memory fact store approaches size thresholds
 * and recommends or triggers a backend switch.
 *
 * <p>Thresholds are derived from measured JVM heap impact:
 * <ul>
 *   <li>100,000 facts — ~50 MB heap; safe for dev</li>
 *   <li>1,000,000 facts — ~500 MB heap; warn, recommend Neo4j</li>
 *   <li>10,000,000 facts — ~5 GB heap; require backend switch or risk OOM</li>
 * </ul>
 */
public final class StoreSizing {
    /** Soft threshold: log a warning when exceeded. */
    public static final long WARN_THRESHOLD = 500_000;

    /** Hard threshold: throw if in-memory store exceeds this. */
    public static final long REJECT_THRESHOLD = 5_000_000;

    private StoreSizing() {
    }

    public enum Recommendation {
        /** Current size is safe for in-memory storage. */
        OK,
        /** Approaching limit; recommend switching to Neo4j or another external store. */
        SWITCH_RECOMMENDED,
        /** Size exceeds safe limit; reject further inserts until backend is switched. */
        REJECT
    }

    public static Recommendation evaluate(long activeFactCount) {
        if (activeFactCount >= REJECT_THRESHOLD) {
            return Recommendation.REJECT;
        }
        if (activeFactCount >= WARN_THRESHOLD) {
            return Recommendation.SWITCH_RECOMMENDED;
        }
        return Recommendation.OK;
    }

    /**
     * Guards inserts into an in-memory store. If the count exceeds thresholds,
     * logs a warning or throws.
     *
     * @return true if the insert should proceed
     * @throws IllegalStateException if the hard threshold is exceeded
     */
    public static boolean guardInsert(FactStore store, String projectId, int batchSize) {
        long current = store.activeFactCount(projectId);
        long projected = current + batchSize;
        Recommendation recommendation = evaluate(projected);
        return switch (recommendation) {
            case OK -> true;
            case SWITCH_RECOMMENDED -> {
                System.err.printf(
                        "[StoreSizing] WARN: project %s has %d active facts (~%.0f MB heap). "
                                + "Consider switching to Neo4j-backed FactStore.%n",
                        projectId, projected, projected * 500.0 / 1_000_000);
                yield true;
            }
            case REJECT -> throw new IllegalStateException(
                    String.format(
                            "Project %s has %d active facts, exceeding the in-memory limit of %d. "
                                    + "Switch to an external FactStore backend.",
                            projectId, projected, REJECT_THRESHOLD));
        };
    }
}
