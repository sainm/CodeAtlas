package org.sainm.codeatlas.analyzers.project;

import org.sainm.codeatlas.analyzers.xml.SafeXmlDocumentLoader;
import org.sainm.codeatlas.graph.project.BuildSystem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public final class ProjectLayoutAnalyzer {
    private static final List<String> STANDARD_SOURCE_ROOTS = List.of(
        "src/main/java",
        "src/main/resources",
        "src/main/webapp",
        "src/test/java",
        "src/test/resources"
    );
    private static final Pattern GRADLE_INCLUDE = Pattern.compile("include\\s+([^\\r\\n]+)");
    private static final Pattern QUOTED_VALUE = Pattern.compile("['\"]([^'\"]+)['\"]");

    private final SafeXmlDocumentLoader xmlLoader = new SafeXmlDocumentLoader();

    public ProjectLayout analyze(String projectId, Path root) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Set<DiscoveredModule> modules = new LinkedHashSet<>();
        if (Files.exists(normalizedRoot.resolve("pom.xml"))) {
            modules.addAll(mavenModules(normalizedRoot));
        }
        Path settingsGradle = normalizedRoot.resolve("settings.gradle");
        Path settingsGradleKts = normalizedRoot.resolve("settings.gradle.kts");
        if (Files.exists(settingsGradle) || Files.exists(settingsGradleKts)) {
            modules.addAll(gradleModules(normalizedRoot, Files.exists(settingsGradle) ? settingsGradle : settingsGradleKts));
        }
        if (modules.isEmpty()) {
            BuildSystem buildSystem = detectBuildSystem(normalizedRoot);
            modules.add(module("_root", normalizedRoot, buildSystem));
        }
        return new ProjectLayout(List.copyOf(modules));
    }

    private List<DiscoveredModule> mavenModules(Path root) {
        List<String> moduleNames = readPomModules(root.resolve("pom.xml"));
        List<DiscoveredModule> modules = new ArrayList<>();
        modules.add(module("_root", root, BuildSystem.MAVEN));
        for (String moduleName : moduleNames) {
            Path modulePath = root.resolve(moduleName).normalize();
            modules.add(module(moduleName, modulePath, BuildSystem.MAVEN));
        }
        return modules;
    }

    private List<String> readPomModules(Path pom) {
        Document document = xmlLoader.load(pom);
        NodeList nodes = document.getElementsByTagName("module");
        List<String> modules = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            String value = nodes.item(i).getTextContent();
            if (value != null && !value.isBlank()) {
                modules.add(value.trim().replace('\\', '/'));
            }
        }
        return modules;
    }

    private List<DiscoveredModule> gradleModules(Path root, Path settingsFile) {
        List<String> moduleNames = readGradleIncludes(settingsFile);
        List<DiscoveredModule> modules = new ArrayList<>();
        modules.add(module("_root", root, BuildSystem.GRADLE));
        for (String moduleName : moduleNames) {
            String pathName = moduleName.replace(':', '/');
            if (pathName.startsWith("/")) {
                pathName = pathName.substring(1);
            }
            if (!pathName.isBlank()) {
                modules.add(module(moduleName, root.resolve(pathName).normalize(), BuildSystem.GRADLE));
            }
        }
        return modules;
    }

    private List<String> readGradleIncludes(Path settingsFile) {
        try {
            String text = Files.readString(settingsFile);
            List<String> modules = new ArrayList<>();
            Matcher includeMatcher = GRADLE_INCLUDE.matcher(text);
            while (includeMatcher.find()) {
                Matcher valueMatcher = QUOTED_VALUE.matcher(includeMatcher.group(1));
                while (valueMatcher.find()) {
                    String module = valueMatcher.group(1).trim();
                    if (!module.isBlank()) {
                        modules.add(module);
                    }
                }
            }
            return modules;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read Gradle settings: " + settingsFile, exception);
        }
    }

    private DiscoveredModule module(String moduleKey, Path basePath, BuildSystem buildSystem) {
        return new DiscoveredModule(moduleKey, basePath, buildSystem, sourceRoots(basePath));
    }

    private List<SourceRootDescriptor> sourceRoots(Path basePath) {
        return STANDARD_SOURCE_ROOTS.stream()
            .map(root -> new SourceRootDescriptor(root, basePath.resolve(root)))
            .filter(sourceRoot -> Files.exists(sourceRoot.path()))
            .toList();
    }

    private BuildSystem detectBuildSystem(Path root) {
        if (Files.exists(root.resolve("pom.xml"))) {
            return BuildSystem.MAVEN;
        }
        if (Files.exists(root.resolve("settings.gradle"))
            || Files.exists(root.resolve("settings.gradle.kts"))
            || Files.exists(root.resolve("build.gradle"))
            || Files.exists(root.resolve("build.gradle.kts"))) {
            return BuildSystem.GRADLE;
        }
        return BuildSystem.UNKNOWN;
    }
}
