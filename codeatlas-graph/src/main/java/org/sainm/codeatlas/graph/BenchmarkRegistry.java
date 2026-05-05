package org.sainm.codeatlas.graph;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Registry of named {@link BenchmarkProfile} instances.
 *
 * <p>CodeAtlas operators add profiles for each target (small, medium, large fixtures).
 * Activation policies for FFM, Tai-e, or cache strategy changes must reference
 * a registered profile and provide evidence it still meets its target.
 */
public final class BenchmarkRegistry {
    private final Map<String, BenchmarkProfile> profiles = new LinkedHashMap<>();

    public BenchmarkRegistry() {
    }

    public static BenchmarkRegistry defaults() {
        BenchmarkRegistry registry = new BenchmarkRegistry();
        registry.register(BenchmarkProfile.define(
                "small-fixture",
                "Small local fixture (5-10 Java files, 1-2 JSP, 2-3 XML)",
                java.time.Duration.ofSeconds(5),
                256L * 1024 * 1024));
        registry.register(BenchmarkProfile.define(
                "impact-report",
                "Fast impact report regression guard",
                java.time.Duration.ofSeconds(30),
                512L * 1024 * 1024));
        return registry;
    }

    public void register(BenchmarkProfile profile) {
        Objects.requireNonNull(profile, "profile");
        profiles.put(profile.name(), profile);
    }

    public Optional<BenchmarkProfile> get(String name) {
        return Optional.ofNullable(profiles.get(name));
    }

    public BenchmarkProfile require(String name) {
        BenchmarkProfile profile = profiles.get(name);
        if (profile == null) {
            throw new IllegalArgumentException("unknown benchmark profile: " + name);
        }
        return profile;
    }

    public List<BenchmarkProfile> all() {
        return List.copyOf(profiles.values());
    }
}
