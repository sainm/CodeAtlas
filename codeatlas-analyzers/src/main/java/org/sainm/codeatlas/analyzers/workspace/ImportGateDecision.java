package org.sainm.codeatlas.analyzers.workspace;

import java.util.List;

public record ImportGateDecision(List<ImportGateIssue> issues) {
    public ImportGateDecision {
        issues = List.copyOf(issues == null ? List.of() : issues);
    }

    public boolean allowed() {
        return issues.stream().noneMatch(issue -> issue.severity() == ImportGateSeverity.BLOCKING);
    }

    public boolean hasBlockingIssue(String code) {
        return hasIssue(ImportGateSeverity.BLOCKING, code);
    }

    public boolean hasWarning(String code) {
        return hasIssue(ImportGateSeverity.WARNING, code);
    }

    private boolean hasIssue(ImportGateSeverity severity, String code) {
        return issues.stream()
                .anyMatch(issue -> issue.severity() == severity && issue.code().equals(code));
    }
}
