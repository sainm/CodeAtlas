package org.sainm.codeatlas.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TestRecommendationContextTest {
    @Test
    void prioritizesTestsByHistoricalRiskAndChangeFrequency() {
        TestRecommendationContext context = TestRecommendationContext.of(
                Map.of("method://shop/src/main/java/UserService#save", 50,
                        "method://shop/src/main/java/AuditService#log", 5),
                Map.of(),
                Map.of("method://shop/src/main/java/UserService#save", 10));

        List<String> tests = List.of("Test A (UserService.save)", "Test B (AuditService.log)");
        List<ImpactPath> paths = List.of(
                new ImpactPath(List.of("method://shop/src/main/java/UserService#save")),
                new ImpactPath(List.of("method://shop/src/main/java/AuditService#log")));

        List<String> prioritized = context.prioritizeTests(tests, paths);

        assertEquals("Test A (UserService.save)", prioritized.getFirst());
    }

    @Test
    void emptyContextReturnsOriginalOrder() {
        TestRecommendationContext context = TestRecommendationContext.empty();
        List<String> tests = List.of("Test A", "Test B");
        List<String> prioritized = context.prioritizeTests(tests, List.of());
        assertEquals(tests, prioritized);
    }
}
