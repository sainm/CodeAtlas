package org.sainm.codeatlas.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class BenchmarkProfileTest {
    @Test
    void recordsMeasurementsAndChecksTarget() {
        BenchmarkProfile profile = BenchmarkProfile.define(
                "test-profile",
                "test target",
                Duration.ofSeconds(5),
                256 * 1024 * 1024L);

        assertFalse(profile.meetsTarget());

        profile.record(new BenchmarkProfile.BenchmarkMeasurement(
                System.currentTimeMillis(),
                Duration.ofSeconds(3),
                Duration.ofSeconds(2),
                100 * 1024 * 1024L,
                1000,
                2000,
                50,
                "local"));

        assertTrue(profile.meetsTarget());
    }

    @Test
    void registryContainsDefaultProfiles() {
        BenchmarkRegistry registry = BenchmarkRegistry.defaults();

        assertTrue(registry.get("small-fixture").isPresent());
        assertTrue(registry.get("impact-report").isPresent());
        assertEquals(Duration.ofSeconds(5), registry.require("small-fixture").targetP95());
        assertEquals(Duration.ofSeconds(30), registry.require("impact-report").targetP95());
    }
}
