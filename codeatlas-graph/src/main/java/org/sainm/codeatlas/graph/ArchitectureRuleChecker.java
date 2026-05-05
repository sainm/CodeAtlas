package org.sainm.codeatlas.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Applies a set of {@link ArchitectureRule} instances to impact paths
 * and collects violations.
 */
public final class ArchitectureRuleChecker {
    private final List<ArchitectureRule> rules;

    public ArchitectureRuleChecker(List<ArchitectureRule> rules) {
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
    }

    public static ArchitectureRuleChecker of(ArchitectureRule... rules) {
        return new ArchitectureRuleChecker(List.of(rules));
    }

    public ArchitectureCheckResult check(List<ImpactPath> paths) {
        Map<String, List<ArchitectureViolation>> violationsByRule = new LinkedHashMap<>();
        for (ArchitectureRule rule : rules) {
            List<ArchitectureViolation> ruleViolations = new ArrayList<>();
            for (ImpactPath path : paths) {
                if (rule.check(path)) {
                    ruleViolations.add(new ArchitectureViolation(
                            rule.id(),
                            rule.severity(),
                            rule.description(),
                            rule.remediation(),
                            path));
                }
            }
            if (!ruleViolations.isEmpty()) {
                violationsByRule.put(rule.id(), ruleViolations);
            }
        }
        return new ArchitectureCheckResult(violationsByRule);
    }

    public record ArchitectureViolation(
            String ruleId,
            ArchitectureRule.RuleSeverity severity,
            String description,
            String remediation,
            ImpactPath path) {
    }

    public record ArchitectureCheckResult(
            Map<String, List<ArchitectureViolation>> violationsByRule) {
        public ArchitectureCheckResult {
            violationsByRule = violationsByRule == null
                    ? Map.of()
                    : Map.copyOf(violationsByRule);
        }

        public boolean hasErrors() {
            return violationsByRule.values().stream()
                    .flatMap(List::stream)
                    .anyMatch(v -> v.severity() == ArchitectureRule.RuleSeverity.ERROR);
        }

        public int violationCount() {
            return violationsByRule.values().stream()
                    .mapToInt(List::size)
                    .sum();
        }
    }
}
