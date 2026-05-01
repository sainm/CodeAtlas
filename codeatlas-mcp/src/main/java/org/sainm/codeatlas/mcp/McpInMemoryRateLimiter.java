package org.sainm.codeatlas.mcp;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class McpInMemoryRateLimiter {
    private final int maxCalls;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public McpInMemoryRateLimiter(int maxCalls, Duration window, Clock clock) {
        this.maxCalls = maxCalls;
        this.window = window == null || window.isZero() || window.isNegative() ? Duration.ofMinutes(1) : window;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public static McpInMemoryRateLimiter unlimited() {
        return new McpInMemoryRateLimiter(Integer.MAX_VALUE, Duration.ofDays(1), Clock.systemUTC());
    }

    public void checkAllowed(String principal) {
        if (maxCalls == Integer.MAX_VALUE) {
            return;
        }
        String key = principal == null || principal.isBlank() ? "anonymous" : principal.trim();
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(clock.instant()));
        synchronized (bucket) {
            Instant now = clock.instant();
            if (!now.isBefore(bucket.windowStart.plus(window))) {
                bucket.windowStart = now;
                bucket.calls = 0;
            }
            if (bucket.calls >= maxCalls) {
                throw new IllegalStateException("MCP rate limit exceeded for principal: " + key);
            }
            bucket.calls++;
        }
    }

    private static final class Bucket {
        private Instant windowStart;
        private int calls;

        private Bucket(Instant windowStart) {
            this.windowStart = windowStart;
        }
    }
}
