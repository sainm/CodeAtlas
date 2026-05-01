package org.sainm.codeatlas.ai;

import java.util.List;
import java.util.Objects;

public record ImpactAssistantResult(
    String summary,
    String riskExplanation,
    List<String> testSuggestions,
    int evidenceCount,
    boolean aiAssisted
) {
    public ImpactAssistantResult {
        summary = clean(summary);
        riskExplanation = clean(riskExplanation);
        Objects.requireNonNull(testSuggestions, "testSuggestions");
        testSuggestions = testSuggestions.stream()
            .map(ImpactAssistantResult::clean)
            .filter(value -> !value.isBlank())
            .toList();
        if (evidenceCount < 0) {
            throw new IllegalArgumentException("evidenceCount must be non-negative");
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
