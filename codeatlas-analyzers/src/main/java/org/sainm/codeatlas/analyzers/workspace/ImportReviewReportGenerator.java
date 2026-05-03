package org.sainm.codeatlas.analyzers.workspace;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ImportReviewReportGenerator {
    private final EntrypointClueDetector entrypointDetector;
    private final BoundaryDiagnosticDetector boundaryDetector;

    public ImportReviewReportGenerator(
            EntrypointClueDetector entrypointDetector,
            BoundaryDiagnosticDetector boundaryDetector) {
        this.entrypointDetector = entrypointDetector == null ? EntrypointClueDetector.defaults() : entrypointDetector;
        this.boundaryDetector = boundaryDetector == null ? BoundaryDiagnosticDetector.defaults() : boundaryDetector;
    }

    public static ImportReviewReportGenerator defaults() {
        return new ImportReviewReportGenerator(EntrypointClueDetector.defaults(), BoundaryDiagnosticDetector.defaults());
    }

    public ImportReviewReport generate(
            WorkspaceInventory inventory,
            WorkspaceLayoutProfile layoutProfile) throws IOException {
        if (inventory == null) {
            throw new IllegalArgumentException("inventory is required");
        }
        if (layoutProfile == null) {
            throw new IllegalArgumentException("layoutProfile is required");
        }
        List<EntrypointClue> entrypoints = entrypointDetector.detect(inventory, layoutProfile);
        List<BoundaryDiagnostic> diagnostics = boundaryDetector.detect(inventory);
        List<BoundaryDiagnostic> blindSpots = diagnostics.stream()
                .filter(diagnostic -> diagnostic.boundary() == AnalysisBoundary.UNSUPPORTED)
                .toList();
        Map<String, ProjectReviewCandidate> projects = buildProjects(layoutProfile, entrypoints, diagnostics);
        List<RecommendedAnalysisScope> scopes = recommendScopes(projects.values(), inventory);
        List<String> confirmationItems = confirmationItems(inventory.mode(), projects.values(), blindSpots);
        boolean requiresConfirmation = !confirmationItems.isEmpty();
        ImportReviewOverview overview = new ImportReviewOverview(
                inventory.workspaceId(),
                inventory.sourceType(),
                inventory.mode(),
                inventory.entries().size(),
                totalSizeBytes(inventory.entries()),
                projects.size());
        return new ImportReviewReport(
                inventory.workspaceId(),
                overview,
                List.copyOf(projects.values()),
                capabilityCoverage(inventory, diagnostics),
                blindSpots,
                confirmationItems,
                scopes,
                AnalysisScopeDecision.empty(inventory.workspaceId()),
                requiresConfirmation);
    }

    private static Map<String, ProjectReviewCandidate> buildProjects(
            WorkspaceLayoutProfile layoutProfile,
            List<EntrypointClue> entrypoints,
            List<BoundaryDiagnostic> diagnostics) {
        Map<String, List<EntrypointClue>> entrypointsByRoot = new LinkedHashMap<>();
        for (EntrypointClue entrypoint : entrypoints) {
            String root = ownerRoot(layoutProfile, entrypoint.evidencePath());
            entrypointsByRoot.computeIfAbsent(root, ignored -> new ArrayList<>()).add(entrypoint);
        }
        Map<String, List<BoundaryDiagnostic>> diagnosticsByRoot = new LinkedHashMap<>();
        for (BoundaryDiagnostic diagnostic : diagnostics) {
            if (diagnostic.boundary() == AnalysisBoundary.UNSUPPORTED) {
                continue;
            }
            String root = ownerRoot(layoutProfile, diagnostic.evidencePath());
            diagnosticsByRoot.computeIfAbsent(root, ignored -> new ArrayList<>()).add(diagnostic);
        }

        Map<String, ProjectReviewCandidate> projects = new LinkedHashMap<>();
        for (ProjectLayoutCandidate candidate : layoutProfile.candidates()) {
            List<EntrypointClue> projectEntrypoints = entrypointsByRoot.getOrDefault(candidate.rootPath(), List.of());
            List<BoundaryDiagnostic> projectDiagnostics = diagnosticsByRoot.getOrDefault(candidate.rootPath(), List.of());
            projects.put(candidate.rootPath(), reviewCandidate(candidate, projectEntrypoints, projectDiagnostics));
        }
        for (Map.Entry<String, List<BoundaryDiagnostic>> entry : diagnosticsByRoot.entrySet()) {
            projects.computeIfAbsent(entry.getKey(), root -> reviewCandidate(
                    new ProjectLayoutCandidate(root, ProjectLayoutType.UNKNOWN_LEGACY, List.of(), List.of(), List.of(), List.of(), List.of()),
                    entrypointsByRoot.getOrDefault(root, List.of()),
                    entry.getValue()));
        }
        for (Map.Entry<String, List<EntrypointClue>> entry : entrypointsByRoot.entrySet()) {
            projects.computeIfAbsent(entry.getKey(), root -> reviewCandidate(
                    new ProjectLayoutCandidate(root, ProjectLayoutType.UNKNOWN_LEGACY, List.of(), List.of(), List.of(), List.of(), List.of()),
                    entry.getValue(),
                    diagnosticsByRoot.getOrDefault(root, List.of())));
        }
        return projects;
    }

    private static ProjectReviewCandidate reviewCandidate(
            ProjectLayoutCandidate candidate,
            List<EntrypointClue> entrypoints,
            List<BoundaryDiagnostic> diagnostics) {
        ProjectReviewStatus status = status(candidate, entrypoints, diagnostics);
        Set<String> evidence = new LinkedHashSet<>(candidate.evidencePaths());
        entrypoints.stream().map(EntrypointClue::evidencePath).forEach(evidence::add);
        diagnostics.stream().map(BoundaryDiagnostic::evidencePath).forEach(evidence::add);
        return new ProjectReviewCandidate(
                candidate.rootPath(),
                candidate.layoutType(),
                status,
                List.copyOf(evidence),
                entrypoints.stream().map(EntrypointClue::evidencePath).toList(),
                diagnostics.stream().map(BoundaryDiagnostic::evidencePath).toList());
    }

    private static ProjectReviewStatus status(
            ProjectLayoutCandidate candidate,
            List<EntrypointClue> entrypoints,
            List<BoundaryDiagnostic> diagnostics) {
        boolean hasJavaScope = !candidate.sourceRoots().isEmpty()
                || entrypoints.stream().anyMatch(ImportReviewReportGenerator::isJavaEntrypoint);
        boolean hasBytecodeScope = !candidate.classpathCandidates().isEmpty();
        boolean hasWebScope = !candidate.webRoots().isEmpty()
                || entrypoints.stream().anyMatch(ImportReviewReportGenerator::isWebEntrypoint);
        boolean hasBuild = candidate.layoutType() == ProjectLayoutType.GRADLE
                || candidate.layoutType() == ProjectLayoutType.MAVEN
                || candidate.layoutType() == ProjectLayoutType.ANT_LIKE;
        if (!diagnostics.isEmpty() && !hasJavaScope && !hasBytecodeScope && !hasWebScope && !hasBuild) {
            return ProjectReviewStatus.BOUNDARY_ONLY;
        }
        if (hasJavaScope || hasBytecodeScope) {
            return ProjectReviewStatus.READY;
        }
        if (hasWebScope) {
            return ProjectReviewStatus.PARTIAL;
        }
        if (hasBuild) {
            return ProjectReviewStatus.PARTIAL;
        }
        if (!entrypoints.isEmpty()) {
            return ProjectReviewStatus.PARTIAL;
        }
        if (!diagnostics.isEmpty()) {
            return ProjectReviewStatus.BOUNDARY_ONLY;
        }
        return ProjectReviewStatus.UNKNOWN;
    }

    private static List<RecommendedAnalysisScope> recommendScopes(
            Iterable<ProjectReviewCandidate> projects,
            WorkspaceInventory inventory) {
        List<ProjectReviewCandidate> projectList = new ArrayList<>();
        projects.forEach(projectList::add);
        List<RecommendedAnalysisScope> scopes = new ArrayList<>();
        for (ProjectReviewCandidate project : projectList) {
            if (project.status() == ProjectReviewStatus.READY
                    && hasOwnedUnderRoot(inventory, project.rootPath(), ".java", projectList)) {
                scopes.add(new RecommendedAnalysisScope(project.rootPath(), "java-source", "Java source roots are available"));
            }
            if (project.status() == ProjectReviewStatus.READY
                    && (hasOwnedUnderRoot(inventory, project.rootPath(), ".jar", projectList)
                            || hasOwnedUnderRoot(inventory, project.rootPath(), ".class", projectList))) {
                scopes.add(new RecommendedAnalysisScope(project.rootPath(), "java-bytecode", "Java bytecode artifacts are available"));
            }
            if (hasOwnedUnderRoot(inventory, project.rootPath(), ".jsp", projectList)
                    || hasOwnedUnderRoot(inventory, project.rootPath(), ".jspx", projectList)) {
                scopes.add(new RecommendedAnalysisScope(project.rootPath(), "jsp-web", "JSP web roots are available"));
            }
            if (project.status() == ProjectReviewStatus.BOUNDARY_ONLY) {
                scopes.add(new RecommendedAnalysisScope(project.rootPath(), "boundary", "Only boundary diagnostics are available"));
            }
        }
        return List.copyOf(scopes);
    }

    private static List<String> confirmationItems(
            ImportMode mode,
            Iterable<ProjectReviewCandidate> projects,
            List<BoundaryDiagnostic> blindSpots) {
        if (mode != ImportMode.ASSISTED_IMPORT_REVIEW) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (ProjectReviewCandidate project : projects) {
            if (project.status() != ProjectReviewStatus.READY) {
                items.add(project.rootPath() + " requires confirmation: " + project.status());
            }
        }
        for (BoundaryDiagnostic blindSpot : blindSpots) {
            items.add(blindSpot.evidencePath() + " requires confirmation: " + blindSpot.boundary());
        }
        return List.copyOf(items);
    }

    private static List<CapabilityArea> capabilityCoverage(
            WorkspaceInventory inventory,
            List<BoundaryDiagnostic> diagnostics) {
        EnumSet<CapabilityArea> areas = EnumSet.noneOf(CapabilityArea.class);
        for (FileInventoryEntry entry : inventory.entries()) {
            String path = entry.relativePath().toLowerCase(Locale.ROOT);
            String name = fileName(path);
            if (entry.level() == FileCapabilityLevel.L5_SKIPPED) {
                areas.add(CapabilityArea.UNSUPPORTED);
                continue;
            }
            if (path.endsWith(".java")) {
                areas.add(CapabilityArea.JAVA_SOURCE);
            }
            if (path.endsWith(".class") || path.endsWith(".jar")) {
                areas.add(CapabilityArea.JAVA_BYTECODE);
            }
            if (path.endsWith(".jsp") || path.endsWith(".jspx")) {
                areas.add(CapabilityArea.JSP_WEB);
            }
            if (path.endsWith(".html") || path.endsWith(".htm") || path.endsWith(".js")) {
                areas.add(CapabilityArea.HTML_JS);
            }
            if (path.endsWith(".sh") || path.endsWith(".bat") || path.endsWith(".cmd") || path.endsWith(".ps1")) {
                areas.add(CapabilityArea.SHELL);
            }
            if (name.equals("build.gradle") || name.equals("settings.gradle") || name.equals("pom.xml")
                    || name.equals("build.xml") || name.equals("makefile") || name.equals("cmakelists.txt")) {
                areas.add(CapabilityArea.BUILD_SYSTEM);
            }
        }
        for (BoundaryDiagnostic diagnostic : diagnostics) {
            switch (diagnostic.boundary()) {
                case NATIVE, C_BOUNDARY -> areas.add(CapabilityArea.C_NATIVE);
                case COBOL_BOUNDARY -> areas.add(CapabilityArea.COBOL);
                case JCL_BOUNDARY -> areas.add(CapabilityArea.JCL);
                case UNSUPPORTED -> areas.add(CapabilityArea.UNSUPPORTED);
                case EXTERNAL -> areas.add(CapabilityArea.BUILD_SYSTEM);
            }
        }
        return List.copyOf(areas);
    }

    private static boolean isJavaEntrypoint(EntrypointClue clue) {
        return clue.kind() == EntrypointKind.MAIN_METHOD
                || clue.kind() == EntrypointKind.SPRING_REQUEST_MAPPING
                || clue.kind() == EntrypointKind.SCHEDULER
                || clue.kind() == EntrypointKind.MESSAGE_LISTENER;
    }

    private static boolean isWebEntrypoint(EntrypointClue clue) {
        return clue.kind() == EntrypointKind.JSP_PAGE
                || clue.kind() == EntrypointKind.HTML_FORM
                || clue.kind() == EntrypointKind.STRUTS_ACTION
                || clue.kind() == EntrypointKind.STATIC_JS_HTTP;
    }

    private static String ownerRoot(WorkspaceLayoutProfile layoutProfile, String evidencePath) {
        String bestRoot = "";
        for (ProjectLayoutCandidate candidate : layoutProfile.candidates()) {
            if (isUnderRoot(evidencePath, candidate.rootPath())) {
                String root = candidate.rootPath();
                if (bestRoot.isBlank() || rootDepth(root) > rootDepth(bestRoot)) {
                    bestRoot = root;
                }
            }
        }
        if (!bestRoot.isBlank()) {
            return bestRoot;
        }
        int slash = evidencePath.indexOf('/');
        return slash > 0 ? evidencePath.substring(0, slash) : ".";
    }

    private static int rootDepth(String root) {
        String normalized = ProjectLayoutCandidate.normalizeRoot(root);
        if (normalized.equals(".")) {
            return 0;
        }
        return normalized.split("/", -1).length;
    }

    private static boolean hasOwnedUnderRoot(
            WorkspaceInventory inventory,
            String root,
            String suffix,
            List<ProjectReviewCandidate> projects) {
        for (FileInventoryEntry entry : inventory.entries()) {
            if (entry.level() == FileCapabilityLevel.L5_SKIPPED) {
                continue;
            }
            if (isUnderRoot(entry.relativePath(), root)
                    && !isUnderNestedProject(entry.relativePath(), root, projects)
                    && entry.relativePath().toLowerCase(Locale.ROOT).endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUnderNestedProject(
            String path,
            String root,
            List<ProjectReviewCandidate> projects) {
        String normalizedRoot = ProjectLayoutCandidate.normalizeRoot(root);
        for (ProjectReviewCandidate project : projects) {
            String candidateRoot = project.rootPath();
            if (candidateRoot.equals(normalizedRoot)) {
                continue;
            }
            if (isUnderRoot(candidateRoot, normalizedRoot) && isUnderRoot(path, candidateRoot)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUnderRoot(String path, String root) {
        String normalizedRoot = ProjectLayoutCandidate.normalizeRoot(root);
        return normalizedRoot.equals(".") || path.equals(normalizedRoot) || path.startsWith(normalizedRoot + "/");
    }

    private static long totalSizeBytes(List<FileInventoryEntry> entries) {
        long total = 0;
        for (FileInventoryEntry entry : entries) {
            total += entry.sizeBytes();
        }
        return total;
    }

    private static String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }
}
