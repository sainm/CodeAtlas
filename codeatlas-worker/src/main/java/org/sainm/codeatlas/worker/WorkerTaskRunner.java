package org.sainm.codeatlas.worker;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class WorkerTaskRunner {
    public <T> WorkerTaskResult<T> run(Callable<T> task, int maxAttempts, Duration timeout) {
        int attempts = Math.max(1, maxAttempts);
        Duration effectiveTimeout = timeout == null || timeout.isNegative() || timeout.isZero()
            ? Duration.ofSeconds(30)
            : timeout;
        Instant startedAt = Instant.now();
        Exception lastFailure = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                var future = executor.submit(task);
                T value = future.get(effectiveTimeout.toMillis(), TimeUnit.MILLISECONDS);
                return new WorkerTaskResult<>(WorkerTaskStatus.SUCCEEDED, value, "", attempt, Duration.between(startedAt, Instant.now()));
            } catch (java.util.concurrent.TimeoutException timeoutException) {
                return new WorkerTaskResult<>(WorkerTaskStatus.TIMED_OUT, null, "Task timed out", attempt, Duration.between(startedAt, Instant.now()));
            } catch (Exception exception) {
                lastFailure = exception;
            } finally {
                executor.shutdownNow();
            }
        }
        String message = lastFailure == null ? "Task failed" : lastFailure.getMessage();
        return new WorkerTaskResult<>(WorkerTaskStatus.FAILED, null, message, attempts, Duration.between(startedAt, Instant.now()));
    }
}
