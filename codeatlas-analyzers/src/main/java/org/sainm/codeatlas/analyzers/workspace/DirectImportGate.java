package org.sainm.codeatlas.analyzers.workspace;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class DirectImportGate {
    private static final Set<String> SAFE_ARCHIVE_EXTENSIONS = Set.of(".zip", ".jar", ".war", ".ear", ".tar", ".gz", ".tgz");

    private DirectImportGate() {
    }

    public static DirectImportGate defaults() {
        return new DirectImportGate();
    }

    public ImportGateDecision evaluate(
            ImportRequest request,
            WorkspaceInventory inventory,
            ImportReviewReport report) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (inventory == null) {
            throw new IllegalArgumentException("inventory is required");
        }
        if (report == null) {
            throw new IllegalArgumentException("report is required");
        }
        if (request.mode() != ImportMode.DIRECT_IMPORT) {
            return new ImportGateDecision(List.of());
        }
        List<ImportGateIssue> issues = new ArrayList<>();
        addUnsafeArchiveIssues(request, issues);
        addUnreadablePathIssues(inventory, issues);
        addWorkspaceShapeIssues(report, issues);
        return new ImportGateDecision(issues);
    }

    private static void addUnsafeArchiveIssues(ImportRequest request, List<ImportGateIssue> issues) {
        if (request.sourceType() != ImportSourceType.UPLOADED_ARCHIVE) {
            return;
        }
        Path archive = request.archivePath().orElse(null);
        String archivePath = archive == null ? "" : archive.toString();
        String lower = archivePath.toLowerCase(Locale.ROOT);
        boolean safeExtension = SAFE_ARCHIVE_EXTENSIONS.stream().anyMatch(lower::endsWith);
        if (!safeExtension) {
            issues.add(ImportGateIssue.blocking(
                    "UNSAFE_ARCHIVE",
                    archive == null ? "" : archive.getFileName().toString(),
                    "uploaded archive type is not allowed for direct import"));
        }
    }

    private static void addUnreadablePathIssues(WorkspaceInventory inventory, List<ImportGateIssue> issues) {
        for (FileInventoryEntry entry : inventory.entries()) {
            String code = entry.decodeDiagnostic().code();
            if (code.equals("DECODE_FAILED")) {
                issues.add(ImportGateIssue.blocking(
                        "UNREADABLE_PATH",
                        entry.relativePath(),
                        "file cannot be decoded safely during direct import"));
            } else if (code.equals("FILE_TOO_LARGE")) {
                issues.add(ImportGateIssue.warning(
                        "FILE_TOO_LARGE",
                        entry.relativePath(),
                        "file is outside direct structured analysis limits"));
            }
        }
    }

    private static void addWorkspaceShapeIssues(ImportReviewReport report, List<ImportGateIssue> issues) {
        boolean hasJava = report.capabilityCoverage().contains(CapabilityArea.JAVA_SOURCE)
                || report.capabilityCoverage().contains(CapabilityArea.JSP_WEB)
                || report.capabilityCoverage().contains(CapabilityArea.BUILD_SYSTEM);
        boolean hasBoundaryOnly = report.capabilityCoverage().contains(CapabilityArea.C_NATIVE)
                || report.capabilityCoverage().contains(CapabilityArea.COBOL)
                || report.capabilityCoverage().contains(CapabilityArea.JCL);
        if (!hasJava && hasBoundaryOnly) {
            issues.add(ImportGateIssue.blocking(
                    "NON_JAVA_WORKSPACE",
                    "",
                    "workspace appears to be non-Java and requires assisted import review"));
        }
        for (ProjectReviewCandidate project : report.projects()) {
            if (project.status() == ProjectReviewStatus.READY
                    && project.layoutType() == ProjectLayoutType.SOURCE_ONLY) {
                issues.add(ImportGateIssue.warning(
                        "LEGACY_JAVA_LAYOUT",
                        project.rootPath(),
                        "source-only Java project has no build metadata"));
            }
        }
    }
}
