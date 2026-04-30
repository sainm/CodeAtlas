package org.sainm.codeatlas.analyzers.java;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.sainm.codeatlas.graph.model.NodeRole;

public record ApplicationRoleRuleSet(List<RoleRule> rules) {
    public ApplicationRoleRuleSet {
        rules = List.copyOf(rules);
    }

    public static ApplicationRoleRuleSet legacyConventions() {
        return new ApplicationRoleRuleSet(List.of(
            new RoleRule(NodeRole.STRUTS_ACTION, List.of("action"), List.of("action", "web")),
            new RoleRule(NodeRole.CONTROLLER, List.of("controller"), List.of("controller")),
            new RoleRule(NodeRole.MAPPER, List.of("mapper"), List.of("mapper")),
            new RoleRule(NodeRole.DAO, List.of("dao", "repository"), List.of("dao", "repository")),
            new RoleRule(NodeRole.SERVICE, List.of("service"), List.of("service")),
            new RoleRule(
                NodeRole.BUSINESS_LOGIC,
                List.of("logic", "blogic", "usecase", "manager", "facade", "interactor"),
                List.of("logic", "business", "businesslogic", "biz", "usecase", "application", "manager", "facade")
            )
        ));
    }

    public NodeRole roleForQualifiedName(String qualifiedName) {
        String normalized = qualifiedName == null ? "" : qualifiedName.toLowerCase(Locale.ROOT);
        String simpleName = normalized.substring(normalized.lastIndexOf('.') + 1);
        return rules.stream()
            .filter(rule -> rule.matches(normalized, simpleName))
            .map(RoleRule::role)
            .findFirst()
            .orElse(NodeRole.CODE_TYPE);
    }

    public record RoleRule(NodeRole role, List<String> simpleNameSuffixes, List<String> packageSegments) {
        public RoleRule {
            simpleNameSuffixes = normalize(simpleNameSuffixes);
            packageSegments = normalize(packageSegments);
        }

        private boolean matches(String normalizedQualifiedName, String simpleName) {
            return simpleNameSuffixes.stream().anyMatch(simpleName::endsWith)
                || packageSegments.stream().anyMatch(segment -> normalizedQualifiedName.contains("." + segment + "."));
        }

        private static List<String> normalize(List<String> values) {
            return Optional.ofNullable(values).orElseGet(List::of).stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT).trim())
                .toList();
        }
    }
}
