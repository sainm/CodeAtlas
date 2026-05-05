package org.sainm.codeatlas.facts;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Provides stable batch ordering and deadlock retry with exponential backoff.
 *
 * <p>Batch operations are ordered by fact key to ensure deterministic processing
 * order, reducing cross-writer deadlock risk.
 */
public final class BatchOperationSupport {
    private final int maxRetries;
    private final Duration baseBackoff;
    private final Duration maxBackoff;

    private BatchOperationSupport(int maxRetries, Duration baseBackoff, Duration maxBackoff) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be non-negative");
        }
        this.maxRetries = maxRetries;
        this.baseBackoff = Objects.requireNonNull(baseBackoff, "baseBackoff");
        this.maxBackoff = Objects.requireNonNull(maxBackoff, "maxBackoff");
    }

    public static BatchOperationSupport defaults() {
        return new BatchOperationSupport(3, Duration.ofMillis(100), Duration.ofSeconds(5));
    }

    public static BatchOperationSupport of(int maxRetries, Duration baseBackoff, Duration maxBackoff) {
        return new BatchOperationSupport(maxRetries, baseBackoff, maxBackoff);
    }

    public List<FactRecord> stableOrder(List<FactRecord> facts) {
        return facts.stream()
                .sorted(Comparator.comparing(FactRecord::factKey))
                .toList();
    }

    public <T> List<T> stableOrderBy(List<T> items, java.util.function.Function<T, String> keyExtractor) {
        return items.stream()
                .sorted(Comparator.comparing(keyExtractor))
                .toList();
    }

    public void withRetry(Runnable operation) {
        withRetry(operation, "batch operation");
    }

    public void withRetry(Runnable operation, String description) {
        int attempt = 0;
        while (true) {
            try {
                operation.run();
                return;
            } catch (RuntimeException exception) {
                attempt++;
                if (attempt > maxRetries || !isRetryable(exception)) {
                    throw exception;
                }
                try {
                    long delayMillis = Math.min(
                            baseBackoff.toMillis() * (1L << (attempt - 1)),
                            maxBackoff.toMillis());
                    Thread.sleep(delayMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(description + " interrupted during retry backoff", interrupted);
                }
            }
        }
    }

    public void insertBatch(FactStore store, List<FactRecord> facts) {
        List<FactRecord> ordered = stableOrder(facts);
        withRetry(() -> store.insertAll(ordered), "batch insert of " + ordered.size() + " facts");
    }

    private static boolean isRetryable(RuntimeException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase();
        return message.contains("deadlock")
                || message.contains("timeout")
                || message.contains("transient")
                || message.contains("connection")
                || message.contains("lock");
    }
}
