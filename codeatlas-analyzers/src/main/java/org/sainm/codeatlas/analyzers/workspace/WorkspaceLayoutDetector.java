package org.sainm.codeatlas.analyzers.workspace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class WorkspaceLayoutDetector {
    private WorkspaceLayoutDetector() {
    }

    public static WorkspaceLayoutDetector defaults() {
        return new WorkspaceLayoutDetector();
    }

    public WorkspaceLayoutProfile detect(WorkspaceInventory inventory) {
        if (inventory == null) {
            throw new IllegalArgumentException("inventory is required");
        }
        Map<String, CandidateBuilder> candidates = new LinkedHashMap<>();
        for (FileInventoryEntry entry : inventory.entries()) {
            String path = entry.relativePath();
            if (!hasProjectBoundarySignal(path, entry.level())) {
                continue;
            }
            String root = inferRoot(path);
            CandidateBuilder builder = candidates.computeIfAbsent(root, CandidateBuilder::new);
            builder.observe(path);
        }

        List<ProjectLayoutCandidate> result = new ArrayList<>();
        for (CandidateBuilder builder : candidates.values()) {
            result.add(builder.build());
        }
        result.sort((left, right) -> left.rootPath().compareTo(right.rootPath()));
        return new WorkspaceLayoutProfile(inventory.workspaceId(), result);
    }

    private static boolean hasProjectBoundarySignal(String path, FileCapabilityLevel level) {
        if (level == FileCapabilityLevel.L5_SKIPPED) {
            return false;
        }
        if (isBytecodeArtifact(path)) {
            return true;
        }
        String name = fileName(path);
        if (name.equals("build.gradle")
                || name.equals("settings.gradle")
                || name.equals("pom.xml")
                || name.equals("build.xml")
                || name.equals(".project")
                || name.equals(".classpath")
                || name.equals("Makefile")
                || name.equals("CMakeLists.txt")) {
            return true;
        }
        return path.equals("src")
                || path.startsWith("src/")
                || path.contains("/src/")
                || path.startsWith("WebRoot/")
                || path.startsWith("WebContent/")
                || path.startsWith("WEB-INF/")
                || path.contains("/WebRoot/")
                || path.contains("/WebContent/")
                || path.contains("/WEB-INF/")
                || level == FileCapabilityLevel.L3_BOUNDARY;
    }

    private static String inferRoot(String path) {
        if (isBytecodeArtifact(path) && !isUnderWebInf(path)) {
            int lib = path.indexOf("/lib/");
            if (path.startsWith("lib/")) {
                return ".";
            }
            if (lib > 0) {
                return path.substring(0, lib);
            }
            String outputRoot = inferBytecodeOutputRoot(path);
            if (!outputRoot.isBlank()) {
                return outputRoot;
            }
        }
        for (String marker : List.of(
                "src/main/java/",
                "src/test/java/",
                "src/main/resources/",
                "src/test/resources/",
                "src/",
                "src/main/webapp/",
                "WebRoot/",
                "WebContent/",
                "WEB-INF/")) {
            if (path.startsWith(marker)) {
                return ".";
            }
        }
        for (String marker : List.of(
                "/src/main/java/",
                "/src/test/java/",
                "/src/main/resources/",
                "/src/test/resources/",
                "/src/",
                "/src/main/webapp/",
                "/WebRoot/",
                "/WebContent/")) {
            int index = path.indexOf(marker);
            if (index > 0) {
                return path.substring(0, index);
            }
        }
        int webInf = path.indexOf("/WEB-INF/");
        if (webInf > 0) {
            String webRoot = path.substring(0, webInf);
            int slash = webRoot.lastIndexOf('/');
            return slash > 0 ? webRoot.substring(0, slash) : ".";
        }
        return parent(path);
    }

    private static String inferBytecodeOutputRoot(String path) {
        for (String marker : List.of(
                "target/classes/",
                "target/test-classes/",
                "build/classes/")) {
            if (path.startsWith(marker)) {
                return ".";
            }
        }
        for (String marker : List.of(
                "/target/classes/",
                "/target/test-classes/",
                "/build/classes/",
                "/build/libs/",
                "/target/")) {
            int index = path.indexOf(marker);
            if (index > 0) {
                return path.substring(0, index);
            }
        }
        if (path.startsWith("target/") || path.startsWith("build/libs/")) {
            return ".";
        }
        return "";
    }

    private static String parent(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "." : path.substring(0, slash);
    }

    private static String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private static String extension(String path) {
        String name = fileName(path).toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : "";
    }

    private static boolean isUnder(String path, String root, String child) {
        String prefix = root.equals(".") ? child : root + "/" + child;
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private static final class CandidateBuilder {
        private final String rootPath;
        private final Set<String> sourceRoots = new LinkedHashSet<>();
        private final Set<String> resourceRoots = new LinkedHashSet<>();
        private final Set<String> webRoots = new LinkedHashSet<>();
        private final Set<String> classpathCandidates = new LinkedHashSet<>();
        private final Set<String> evidencePaths = new LinkedHashSet<>();
        private ProjectLayoutType layoutType;

        CandidateBuilder(String rootPath) {
            this.rootPath = ProjectLayoutCandidate.normalizeRoot(rootPath);
        }

        void observe(String path) {
            String name = fileName(path);
            if (name.equals("build.gradle") || name.equals("settings.gradle")) {
                mark(ProjectLayoutType.GRADLE, path);
            } else if (name.equals("pom.xml")) {
                mark(ProjectLayoutType.MAVEN, path);
            } else if (name.equals("build.xml")) {
                mark(ProjectLayoutType.ANT_LIKE, path);
            } else if (name.equals(".project") || name.equals(".classpath")) {
                mark(ProjectLayoutType.ECLIPSE_IDE_ONLY, path);
            }

            addKnownRoot(path, "src/main/resources", resourceRoots);
            addKnownRoot(path, "src/test/resources", resourceRoots);
            addKnownRoot(path, "src/main/webapp", webRoots);
            addKnownRoot(path, "WebRoot", webRoots);
            addKnownRoot(path, "WebContent", webRoots);
            addWebInfRoot(path);
            boolean hasStandardSourceRoot = addKnownRoot(path, "src/main/java", sourceRoots)
                    | addKnownRoot(path, "src/test/java", sourceRoots);
            if (!hasStandardSourceRoot && !isUnderWebRoot(path) && !isUnderResourceRoot(path)) {
                addKnownRoot(path, "src", sourceRoots);
            }
            if (name.equals(".classpath") || isBytecodeArtifact(path)) {
                classpathCandidates.add(path);
            }
            if (layoutType == null && (!sourceRoots.isEmpty() || !webRoots.isEmpty())) {
                layoutType = sourceRoots.isEmpty() ? ProjectLayoutType.UNKNOWN_LEGACY : ProjectLayoutType.SOURCE_ONLY;
            }
        }

        private boolean addKnownRoot(String path, String child, Set<String> roots) {
            if (isUnder(path, rootPath, child)) {
                roots.add(rootPath.equals(".") ? child : rootPath + "/" + child);
                return true;
            }
            return false;
        }

        private void addWebInfRoot(String path) {
            if (path.startsWith("WEB-INF/")) {
                webRoots.add(".");
                return;
            }
            int index = path.indexOf("/WEB-INF/");
            if (index > 0) {
                webRoots.add(path.substring(0, index));
            }
        }

        private boolean isUnderWebRoot(String path) {
            for (String webRoot : webRoots) {
                if (path.equals(webRoot) || path.startsWith(webRoot + "/")) {
                    return true;
                }
            }
            return false;
        }

        private boolean isUnderResourceRoot(String path) {
            for (String resourceRoot : resourceRoots) {
                if (path.equals(resourceRoot) || path.startsWith(resourceRoot + "/")) {
                    return true;
                }
            }
            return false;
        }

        private void mark(ProjectLayoutType type, String evidencePath) {
            if (layoutType == null || priority(type) < priority(layoutType)) {
                layoutType = type;
            }
            evidencePaths.add(evidencePath);
        }

        private ProjectLayoutCandidate build() {
            ProjectLayoutType type = layoutType == null ? ProjectLayoutType.UNKNOWN_LEGACY : layoutType;
            return new ProjectLayoutCandidate(
                    rootPath,
                    type,
                    List.copyOf(sourceRoots),
                    List.copyOf(resourceRoots),
                    List.copyOf(webRoots),
                    List.copyOf(classpathCandidates),
                    List.copyOf(evidencePaths));
        }

        private static int priority(ProjectLayoutType type) {
            return switch (type) {
                case GRADLE -> 1;
                case MAVEN -> 2;
                case ANT_LIKE -> 3;
                case ECLIPSE_IDE_ONLY -> 4;
                case SOURCE_ONLY -> 5;
                case UNKNOWN_LEGACY -> 6;
            };
        }
    }

    private static boolean isBytecodeArtifact(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jar") || lower.endsWith(".class");
    }

    private static boolean isUnderWebInf(String path) {
        return path.startsWith("WEB-INF/") || path.contains("/WEB-INF/");
    }
}
