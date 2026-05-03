package org.sainm.codeatlas.analyzers.workspace;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AnalysisScopeDecisionGenerator {
    private AnalysisScopeDecisionGenerator() {
    }

    public static AnalysisScopeDecisionGenerator defaults() {
        return new AnalysisScopeDecisionGenerator();
    }

    public AnalysisScopeDecision confirm(
            ImportReviewReport report,
            AnalysisScopeDecisionRequest request) {
        if (report == null) {
            throw new IllegalArgumentException("report is required");
        }
        AnalysisScopeDecisionRequest normalized = request == null ? AnalysisScopeDecisionRequest.empty() : request;
        List<AnalysisScopeAuditEntry> auditEntries = new ArrayList<>();
        Set<String> included = new HashSet<>(normalized.includedProjectRoots());
        Set<String> excluded = new HashSet<>(normalized.excludedProjectRoots());
        Set<String> shared = new HashSet<>(normalized.sharedLibraryRoots());

        for (ProjectReviewCandidate project : report.projects()) {
            String root = project.rootPath();
            if (excluded.contains(root)) {
                auditEntries.add(new AnalysisScopeAuditEntry(root, AnalysisScopeDisposition.EXCLUDED, "user excluded project"));
            } else if (shared.contains(root)) {
                auditEntries.add(new AnalysisScopeAuditEntry(root, AnalysisScopeDisposition.SHARED_LIBRARY, "project marked as shared library"));
            } else if (included.contains(root) || (included.isEmpty() && project.status() == ProjectReviewStatus.READY)) {
                auditEntries.add(new AnalysisScopeAuditEntry(root, AnalysisScopeDisposition.INCLUDED, "project selected for analysis"));
            } else if (project.status() == ProjectReviewStatus.BOUNDARY_ONLY
                    || project.status() == ProjectReviewStatus.UNSUPPORTED
                    || project.status() == ProjectReviewStatus.UNKNOWN) {
                auditEntries.add(new AnalysisScopeAuditEntry(root, AnalysisScopeDisposition.UNANALYZABLE, project.status().name()));
            } else {
                auditEntries.add(new AnalysisScopeAuditEntry(root, AnalysisScopeDisposition.DEGRADED, project.status().name()));
            }
        }
        for (String root : normalized.sharedLibraryRoots()) {
            if (report.projects().stream().noneMatch(project -> project.rootPath().equals(root))) {
                auditEntries.add(new AnalysisScopeAuditEntry(root, AnalysisScopeDisposition.SHARED_LIBRARY, "external shared library"));
            }
        }

        return new AnalysisScopeDecision(
                report.workspaceId(),
                normalized.includedProjectRoots(),
                normalized.excludedProjectRoots(),
                normalized.sharedLibraryRoots(),
                normalized.additionalDependencyPaths(),
                normalized.sourceRoots(),
                normalized.libraryRoots(),
                normalized.webRoots(),
                normalized.scriptRoots(),
                normalized.ignoredDirectories(),
                auditEntries);
    }
}
