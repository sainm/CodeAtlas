package org.sainm.codeatlas.graph;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * An architecture rule that can be checked against the fact graph.
 *
 * <p>Rules define constraints on module dependencies, layering violations,
 * cyclic dependencies, and naming conventions.
 */
public final class ArchitectureRule {
    private final String id;
    private final String name;
    private final String description;
    private final RuleSeverity severity;
    private final Predicate<ImpactPath> predicate;
    private final String remediation;

    private ArchitectureRule(
            String id,
            String name,
            String description,
            RuleSeverity severity,
            Predicate<ImpactPath> predicate,
            String remediation) {
        this.id = requireNonBlank(id, "id");
        this.name = requireNonBlank(name, "name");
        this.description = requireNonBlank(description, "description");
        this.severity = Objects.requireNonNull(severity, "severity");
        this.predicate = Objects.requireNonNull(predicate, "predicate");
        this.remediation = remediation == null ? "" : remediation;
    }

    public static ArchitectureRule of(
            String id,
            String name,
            String description,
            RuleSeverity severity,
            Predicate<ImpactPath> predicate,
            String remediation) {
        return new ArchitectureRule(id, name, description, severity, predicate, remediation);
    }

    public boolean check(ImpactPath path) {
        return predicate.test(path);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public RuleSeverity severity() {
        return severity;
    }

    public String remediation() {
        return remediation;
    }

    public enum RuleSeverity {
        ERROR,
        WARNING,
        INFO
    }

    /**
     * Checks whether a path crosses a forbidden layer boundary.
     */
    public static ArchitectureRule noLayerViolation(
            String id,
            String fromLayer,
            String toLayer,
            String description) {
        return ArchitectureRule.of(
                id,
                "No " + fromLayer + " -> " + toLayer,
                description,
                RuleSeverity.ERROR,
                path -> {
                    List<String> ids = path.identityIds();
                    if (ids.size() != 2) return false;
                    String first = ids.getFirst();
                    String last = ids.getLast();
                    return first.contains(fromLayer) && last.contains(toLayer);
                },
                "Refactor to avoid direct dependency from " + fromLayer + " to " + toLayer);
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
