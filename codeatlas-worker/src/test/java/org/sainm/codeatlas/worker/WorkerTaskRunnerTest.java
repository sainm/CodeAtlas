package org.sainm.codeatlas.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WorkerTaskRunnerTest {
    @Test
    void retriesUntilTaskSucceeds() {
        AtomicInteger attempts = new AtomicInteger();

        WorkerTaskResult<String> result = new WorkerTaskRunner().run(() -> {
            if (attempts.incrementAndGet() < 2) {
                throw new IllegalStateException("boom");
            }
            return "ok";
        }, 3, Duration.ofSeconds(1));

        assertEquals(WorkerTaskStatus.SUCCEEDED, result.status());
        assertEquals("ok", result.value());
        assertEquals(2, result.attempts());
    }

    @Test
    void reportsTimeout() {
        WorkerTaskResult<String> result = new WorkerTaskRunner().run(() -> {
            Thread.sleep(500);
            return "late";
        }, 1, Duration.ofMillis(20));

        assertEquals(WorkerTaskStatus.TIMED_OUT, result.status());
        assertTrue(result.errorMessage().contains("timed out"));
    }
}
