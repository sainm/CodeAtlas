package org.sainm.codeatlas.analyzers.workspace;

import java.util.List;

public record ProjectLayoutCandidate(
        String rootPath,
        ProjectLayoutType layoutType,
        List<String> sourceRoots,
        List<String> resourceRoots,
        List<String> webRoots,
        List<String> classpathCandidates,
        List<String> evidencePaths) {
    public ProjectLayoutCandidate {
        rootPath = normalizeRoot(rootPath);
        if (layoutType == null) {
            throw new IllegalArgumentException("layoutType is required");
        }
        sourceRoots = copyNormalized(sourceRoots);
        resourceRoots = copyNormalized(resourceRoots);
        webRoots = copyNormalized(webRoots);
        classpathCandidates = copyNormalized(classpathCandidates);
        evidencePaths = copyNormalized(evidencePaths);
    }

    static String normalizeRoot(String rootPath) {
        if (rootPath == null || rootPath.isBlank() || rootPath.equals(".")) {
            return ".";
        }
        return FileInventoryEntry.normalizeRelativePath(rootPath);
    }

    private static List<String> copyNormalized(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return List.of();
        }
        return paths.stream()
                .map(ProjectLayoutCandidate::normalizeRoot)
                .distinct()
                .sorted()
                .toList();
    }
}
