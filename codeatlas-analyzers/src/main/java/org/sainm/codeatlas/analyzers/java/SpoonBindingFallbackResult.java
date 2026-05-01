package org.sainm.codeatlas.analyzers.java;

public record SpoonBindingFallbackResult(
    JavaAnalysisResult analysis,
    SpoonBindingFallbackMode mode,
    String fallbackReason
) {
    public SpoonBindingFallbackResult {
        if (analysis == null) {
            throw new IllegalArgumentException("analysis is required");
        }
        mode = mode == null ? SpoonBindingFallbackMode.NO_CLASSPATH_FALLBACK : mode;
        fallbackReason = fallbackReason == null || fallbackReason.isBlank() ? null : fallbackReason.trim();
    }
}
