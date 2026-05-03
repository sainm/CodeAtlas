package org.sainm.codeatlas.analyzers.workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BoundaryDiagnosticDetector {
    private static final Pattern COBOL_PROGRAM_ID = Pattern.compile("\\bPROGRAM-ID\\.\\s*([A-Za-z0-9_-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern JCL_PROGRAM = Pattern.compile("\\bPGM=([A-Za-z0-9_.$#@-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern C_FUNCTION = Pattern.compile("\\b[A-Za-z_][\\w\\s\\*]*\\s+([A-Za-z_][\\w]*)\\s*\\([^;]*\\)\\s*\\{");

    private BoundaryDiagnosticDetector() {
    }

    public static BoundaryDiagnosticDetector defaults() {
        return new BoundaryDiagnosticDetector();
    }

    public List<BoundaryDiagnostic> detect(WorkspaceInventory inventory) throws IOException {
        if (inventory == null) {
            throw new IllegalArgumentException("inventory is required");
        }
        List<BoundaryDiagnostic> diagnostics = new ArrayList<>();
        for (FileInventoryEntry entry : inventory.entries()) {
            String path = entry.relativePath();
            String lower = path.toLowerCase(Locale.ROOT);
            if (entry.level() == FileCapabilityLevel.L5_SKIPPED) {
                diagnostics.add(new BoundaryDiagnostic(
                        AnalysisBoundary.UNSUPPORTED,
                        path,
                        "",
                        entry.decodeDiagnostic().code()));
                continue;
            }
            if (isNativeBinary(lower)) {
                diagnostics.add(new BoundaryDiagnostic(AnalysisBoundary.NATIVE, path, baseName(path), "native library boundary"));
            } else if (isCSource(lower)) {
                diagnostics.add(new BoundaryDiagnostic(AnalysisBoundary.C_BOUNDARY, path, firstMatch(inventory, path, C_FUNCTION), "C/C++ boundary"));
            } else if (isCobol(lower)) {
                diagnostics.add(new BoundaryDiagnostic(AnalysisBoundary.COBOL_BOUNDARY, path, firstMatch(inventory, path, COBOL_PROGRAM_ID), "COBOL boundary"));
            } else if (lower.endsWith(".jcl")) {
                diagnostics.add(new BoundaryDiagnostic(AnalysisBoundary.JCL_BOUNDARY, path, firstMatch(inventory, path, JCL_PROGRAM), "JCL boundary"));
            } else if (isExternalBuildBoundary(path)) {
                diagnostics.add(new BoundaryDiagnostic(AnalysisBoundary.EXTERNAL, path, baseName(path), "external build boundary"));
            }
        }
        return List.copyOf(diagnostics);
    }

    private static String firstMatch(WorkspaceInventory inventory, String path, Pattern pattern) throws IOException {
        String content = Files.readString(inventory.sourceRoot().resolve(path), StandardCharsets.UTF_8);
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(1) : baseName(path);
    }

    private static boolean isNativeBinary(String lowerPath) {
        return lowerPath.endsWith(".dll") || lowerPath.endsWith(".so") || lowerPath.endsWith(".a")
                || lowerPath.endsWith(".lib") || lowerPath.endsWith(".exe");
    }

    private static boolean isCSource(String lowerPath) {
        return lowerPath.endsWith(".c") || lowerPath.endsWith(".h") || lowerPath.endsWith(".cpp")
                || lowerPath.endsWith(".hpp");
    }

    private static boolean isCobol(String lowerPath) {
        return lowerPath.endsWith(".cbl") || lowerPath.endsWith(".cob") || lowerPath.endsWith(".cpy");
    }

    private static boolean isExternalBuildBoundary(String path) {
        String name = fileName(path);
        return name.equals("Makefile") || name.equals("CMakeLists.txt");
    }

    private static String baseName(String path) {
        String name = fileName(path);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }
}
