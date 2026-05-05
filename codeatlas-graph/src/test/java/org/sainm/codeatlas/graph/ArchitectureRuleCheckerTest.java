package org.sainm.codeatlas.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ArchitectureRuleCheckerTest {
    @Test
    void detectsLayerViolations() {
        ArchitectureRule rule = ArchitectureRule.noLayerViolation(
                "no-ui-to-db",
                "web",
                "db-table",
                "UI layer must not directly access database tables");

        ImpactPath violating = new ImpactPath(List.of(
                "jsp-page://shop/web-root/page.jsp",
                "db-table://shop/mainDs/public/users"));
        ImpactPath ok = new ImpactPath(List.of(
                "jsp-page://shop/web-root/page.jsp",
                "method://shop/src/main/java/UserService#load()V",
                "db-table://shop/mainDs/public/users"));

        ArchitectureRuleChecker checker = ArchitectureRuleChecker.of(rule);
        ArchitectureRuleChecker.ArchitectureCheckResult result = checker.check(List.of(violating, ok));

        assertTrue(result.hasErrors());
        assertEquals(1, result.violationCount());
    }

    @Test
    void emptyPathsProduceNoViolations() {
        ArchitectureRule rule = ArchitectureRule.noLayerViolation(
                "no-svc-to-db",
                "service",
                "db-table",
                "No direct service to db");

        ArchitectureRuleChecker checker = ArchitectureRuleChecker.of(rule);
        ArchitectureRuleChecker.ArchitectureCheckResult result = checker.check(List.of());

        assertFalse(result.hasErrors());
        assertEquals(0, result.violationCount());
    }
}
