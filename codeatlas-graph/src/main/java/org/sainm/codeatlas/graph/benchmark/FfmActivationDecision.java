package org.sainm.codeatlas.graph.benchmark;

import java.util.List;

public record FfmActivationDecision(
    boolean recommended,
    String summary,
    List<String> reasons
) {
    public FfmActivationDecision {
        summary = summary == null || summary.isBlank() ? (recommended ? "FFM candidate" : "Keep JVM adjacency cache") : summary.trim();
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
