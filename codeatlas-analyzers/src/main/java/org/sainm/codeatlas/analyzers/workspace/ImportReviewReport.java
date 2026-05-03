package org.sainm.codeatlas.analyzers.workspace;

import java.util.List;

public record ImportReviewReport(
        String workspaceId,
        ImportReviewOverview overview,
        List<ProjectReviewCandidate> projects,
        List<CapabilityArea> capabilityCoverage,
        List<BoundaryDiagnostic> blindSpots,
        List<String> userConfirmationItems,
        List<RecommendedAnalysisScope> recommendedAnalysisScopes,
        AnalysisScopeDecision analysisScopeDecision,
        boolean requiresUserConfirmation) {
    public ImportReviewReport {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId is required");
        }
        if (overview == null) {
            throw new IllegalArgumentException("overview is required");
        }
        projects = List.copyOf(projects == null ? List.of() : projects);
        capabilityCoverage = List.copyOf(capabilityCoverage == null ? List.of() : capabilityCoverage);
        blindSpots = List.copyOf(blindSpots == null ? List.of() : blindSpots);
        userConfirmationItems = List.copyOf(userConfirmationItems == null ? List.of() : userConfirmationItems);
        recommendedAnalysisScopes = List.copyOf(recommendedAnalysisScopes == null ? List.of() : recommendedAnalysisScopes);
        analysisScopeDecision = analysisScopeDecision == null
                ? AnalysisScopeDecision.empty(workspaceId)
                : analysisScopeDecision;
    }

    public ProjectReviewCandidate requireProject(String rootPath) {
        String normalized = ProjectLayoutCandidate.normalizeRoot(rootPath);
        for (ProjectReviewCandidate project : projects) {
            if (project.rootPath().equals(normalized)) {
                return project;
            }
        }
        throw new IllegalArgumentException("No import review project for root: " + rootPath);
    }

    public ImportReviewReport withAnalysisScopeDecision(AnalysisScopeDecision decision) {
        if (decision == null) {
            throw new IllegalArgumentException("decision is required");
        }
        if (!decision.workspaceId().equals(workspaceId)) {
            throw new IllegalArgumentException("decision workspaceId must match report workspaceId");
        }
        return new ImportReviewReport(
                workspaceId,
                overview,
                projects,
                capabilityCoverage,
                blindSpots,
                userConfirmationItems,
                recommendedAnalysisScopes,
                decision,
                requiresUserConfirmation);
    }
}
