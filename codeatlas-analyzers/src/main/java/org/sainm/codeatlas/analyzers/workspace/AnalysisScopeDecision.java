package org.sainm.codeatlas.analyzers.workspace;

import java.util.List;

public record AnalysisScopeDecision(
        String workspaceId,
        List<String> includedProjectRoots,
        List<String> excludedProjectRoots,
        List<String> sharedLibraryRoots,
        List<String> additionalDependencyPaths,
        List<String> sourceRoots,
        List<String> libraryRoots,
        List<String> webRoots,
        List<String> scriptRoots,
        List<String> ignoredDirectories,
        List<AnalysisScopeAuditEntry> auditEntries) {
    public AnalysisScopeDecision {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId is required");
        }
        includedProjectRoots = copyNormalized(includedProjectRoots);
        excludedProjectRoots = copyNormalized(excludedProjectRoots);
        sharedLibraryRoots = copyNormalized(sharedLibraryRoots);
        additionalDependencyPaths = copyNormalized(additionalDependencyPaths);
        sourceRoots = copyNormalized(sourceRoots);
        libraryRoots = copyNormalized(libraryRoots);
        webRoots = copyNormalized(webRoots);
        scriptRoots = copyNormalized(scriptRoots);
        ignoredDirectories = copyNormalized(ignoredDirectories);
        auditEntries = List.copyOf(auditEntries == null ? List.of() : auditEntries);
    }

    public static AnalysisScopeDecision empty(String workspaceId) {
        return new AnalysisScopeDecision(
                workspaceId,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private static List<String> copyNormalized(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return List.of();
        }
        return paths.stream()
                .map(FileInventoryEntry::normalizeRelativePath)
                .distinct()
                .sorted()
                .toList();
    }
}
