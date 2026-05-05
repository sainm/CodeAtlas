package org.sainm.codeatlas.graph;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Context for enhancing test recommendations with historical risk,
 * ownership, and change frequency signals.
 */
public final class TestRecommendationContext {
    private final Map<String, Integer> historicalRiskScores;
    private final Map<String, String> ownership;
    private final Map<String, Integer> changeFrequency;

    private TestRecommendationContext(
            Map<String, Integer> historicalRiskScores,
            Map<String, String> ownership,
            Map<String, Integer> changeFrequency) {
        this.historicalRiskScores = Map.copyOf(historicalRiskScores);
        this.ownership = Map.copyOf(ownership);
        this.changeFrequency = Map.copyOf(changeFrequency);
    }

    public static TestRecommendationContext empty() {
        return new TestRecommendationContext(Map.of(), Map.of(), Map.of());
    }

    public static TestRecommendationContext of(
            Map<String, Integer> historicalRiskScores,
            Map<String, String> ownership,
            Map<String, Integer> changeFrequency) {
        return new TestRecommendationContext(
                historicalRiskScores == null ? Map.of() : historicalRiskScores,
                ownership == null ? Map.of() : ownership,
                changeFrequency == null ? Map.of() : changeFrequency);
    }

    public int riskScore(String symbolId) {
        return historicalRiskScores.getOrDefault(symbolId, 0);
    }

    public String owner(String symbolId) {
        return ownership.getOrDefault(symbolId, "unknown");
    }

    public int changeCount(String symbolId) {
        return changeFrequency.getOrDefault(symbolId, 0);
    }

    public List<String> prioritizeTests(List<String> suggestedTests, List<ImpactPath> paths) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        for (String test : suggestedTests) {
            int score = 0;
            for (ImpactPath path : paths) {
                for (String id : path.identityIds()) {
                    score += riskScore(id);
                    score += changeCount(id);
                }
            }
            scores.put(test, score);
        }
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();
    }
}
