package org.sainm.codeatlas.symbols;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SymbolIdNormalizer {
    private static final List<String> DEFAULT_JAVA_SOURCE_ROOTS = List.of("src/main/java");

    private SymbolIdNormalizer() {
    }

    public static SymbolId javaMethod(
            SymbolContext context,
            String physicalPath,
            String ownerBinaryName,
            String methodName,
            String erasedDescriptor) {
        return javaMethod(context, DEFAULT_JAVA_SOURCE_ROOTS, physicalPath, ownerBinaryName, methodName, erasedDescriptor);
    }

    public static SymbolId javaMethod(
            SymbolContext context,
            List<String> sourceRoots,
            String physicalPath,
            String ownerBinaryName,
            String methodName,
            String erasedDescriptor) {
        requireNonBlank(physicalPath, "physicalPath");
        requireNonBlank(ownerBinaryName, "ownerBinaryName");
        requireNonBlank(methodName, "methodName");
        requireNonBlank(erasedDescriptor, "erasedDescriptor");

        String relative = relativize(context.workspaceRoot(), physicalPath);
        String normalizedRelative = normalizeSeparators(relative);
        List<String> normalizedSourceRoots = normalizeSourceRoots(sourceRoots);
        String sourceRootKey = findSourceRoot(normalizedRelative, normalizedSourceRoots, physicalPath);

        String canonical = "method://"
                + context.projectKey()
                + "/"
                + context.moduleKey()
                + "/"
                + sourceRootKey
                + "/"
                + ownerBinaryName
                + "#"
                + methodName
                + erasedDescriptor;
        return SymbolIdParser.withSourceRoots(normalizedSourceRoots).parseId(canonical);
    }

    private static String relativize(String root, String path) {
        String normalizedRoot = normalizeSeparators(root);
        String normalizedPath = normalizeSeparators(path);
        if (normalizedPath.equals(normalizedRoot)) {
            return "";
        }
        String prefix = normalizedRoot.endsWith("/") ? normalizedRoot : normalizedRoot + "/";
        if (!normalizedPath.startsWith(prefix)) {
            throw new IllegalArgumentException("Path is outside workspace root: " + path);
        }
        return normalizedPath.substring(prefix.length());
    }

    private static String normalizeSeparators(String value) {
        return value.replace('\\', '/');
    }

    private static String findSourceRoot(String normalizedRelative, List<String> sourceRoots, String physicalPath) {
        for (String sourceRoot : sourceRoots) {
            if (normalizedRelative.startsWith(sourceRoot + "/")) {
                return sourceRoot;
            }
        }
        throw new IllegalArgumentException("Java method path is not under configured source roots: " + physicalPath);
    }

    private static List<String> normalizeSourceRoots(List<String> sourceRoots) {
        if (sourceRoots == null || sourceRoots.isEmpty()) {
            throw new IllegalArgumentException("sourceRoots are required");
        }
        List<String> normalized = new ArrayList<>();
        for (String sourceRoot : sourceRoots) {
            requireNonBlank(sourceRoot, "sourceRoot");
            String value = normalizeSeparators(sourceRoot);
            if (value.startsWith("/") || value.endsWith("/") || value.contains("//") || value.contains(":/")
                    || value.contains("..")) {
                throw new IllegalArgumentException("source root must be logical and relative: " + sourceRoot);
            }
            normalized.add(value);
        }
        normalized.sort(Comparator.comparingInt(String::length).reversed());
        return List.copyOf(normalized);
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
