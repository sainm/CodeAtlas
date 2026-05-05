package org.sainm.codeatlas.graph;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Named benchmark profile with measurement results and activation policy.
 *
 * <p>Every performance-sensitive change must reference a named benchmark profile
 * and provide evidence that the profile still meets its target.
 */
public final class BenchmarkProfile {
    private final String name;
    private final String targetDescription;
    private final Duration targetP95;
    private final long targetMaxHeapBytes;
    private final List<BenchmarkMeasurement> measurements;

    private BenchmarkProfile(
            String name,
            String targetDescription,
            Duration targetP95,
            long targetMaxHeapBytes) {
        this.name = requireNonBlank(name, "name");
        this.targetDescription = requireNonBlank(targetDescription, "targetDescription");
        this.targetP95 = Objects.requireNonNull(targetP95, "targetP95");
        this.targetMaxHeapBytes = targetMaxHeapBytes;
        this.measurements = new ArrayList<>();
    }

    public static BenchmarkProfile define(
            String name,
            String targetDescription,
            Duration targetP95,
            long targetMaxHeapBytes) {
        return new BenchmarkProfile(name, targetDescription, targetP95, targetMaxHeapBytes);
    }

    public String name() {
        return name;
    }

    public String targetDescription() {
        return targetDescription;
    }

    public Duration targetP95() {
        return targetP95;
    }

    public long targetMaxHeapBytes() {
        return targetMaxHeapBytes;
    }

    public BenchmarkProfile record(BenchmarkMeasurement measurement) {
        measurements.add(Objects.requireNonNull(measurement, "measurement"));
        return this;
    }

    public List<BenchmarkMeasurement> measurements() {
        return List.copyOf(measurements);
    }

    public boolean meetsTarget() {
        if (measurements.isEmpty()) {
            return false;
        }
        BenchmarkMeasurement last = measurements.getLast();
        return last.p95Duration().compareTo(targetP95) <= 0
                && last.heapBytes() <= targetMaxHeapBytes;
    }

    public record BenchmarkMeasurement(
            long timestampEpochMillis,
            Duration p95Duration,
            Duration avgDuration,
            long heapBytes,
            int factCount,
            int edgeCount,
            int pathCount,
            String environment) {
        public BenchmarkMeasurement {
            if (timestampEpochMillis <= 0) {
                throw new IllegalArgumentException("timestampEpochMillis required");
            }
            p95Duration = Objects.requireNonNull(p95Duration, "p95Duration");
            avgDuration = Objects.requireNonNull(avgDuration, "avgDuration");
            if (heapBytes < 0 || factCount < 0 || edgeCount < 0 || pathCount < 0) {
                throw new IllegalArgumentException("counts must be non-negative");
            }
            environment = requireNonBlank(environment, "environment");
        }
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
