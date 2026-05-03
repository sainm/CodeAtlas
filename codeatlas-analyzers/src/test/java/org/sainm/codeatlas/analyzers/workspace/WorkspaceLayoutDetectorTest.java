package org.sainm.codeatlas.analyzers.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceLayoutDetectorTest {
    @TempDir
    Path tempDir;

    @Test
    void detectsBuildAndIdeLayoutCandidates() throws IOException {
        write("gradle-app/build.gradle", "plugins {}\n");
        write("gradle-app/src/main/java/App.java", "class App {}\n");
        write("maven-lib/pom.xml", "<project />\n");
        write("ant-old/build.xml", "<project><target name=\"compile\" /></project>\n");
        write("eclipse-web/.project", "<projectDescription />\n");
        write("eclipse-web/.classpath", "<classpath />\n");

        WorkspaceLayoutProfile profile = detect();

        assertEquals(ProjectLayoutType.GRADLE, profile.requireCandidate("gradle-app").layoutType());
        assertEquals(ProjectLayoutType.MAVEN, profile.requireCandidate("maven-lib").layoutType());
        assertEquals(ProjectLayoutType.ANT_LIKE, profile.requireCandidate("ant-old").layoutType());
        assertEquals(ProjectLayoutType.ECLIPSE_IDE_ONLY, profile.requireCandidate("eclipse-web").layoutType());
        assertEquals(1, profile.requireCandidate("gradle-app").sourceRoots().size());
        assertTrue(profile.requireCandidate("gradle-app").sourceRoots().contains("gradle-app/src/main/java"));
        assertTrue(profile.requireCandidate("eclipse-web").classpathCandidates().contains("eclipse-web/.classpath"));
    }

    @Test
    void detectsSourceOnlyAndLegacyWebRoots() throws IOException {
        write("source-only/src/com/acme/App.java", "package com.acme; class App {}\n");
        write("legacy-web/WebRoot/WEB-INF/web.xml", "<web-app />\n");
        write("legacy-web/WebRoot/WEB-INF/lib/framework.jar", new byte[] {1, 2, 3});
        write("legacy-web/WebRoot/index.jsp", "<html />\n");

        WorkspaceLayoutProfile profile = detect();

        assertEquals(ProjectLayoutType.SOURCE_ONLY, profile.requireCandidate("source-only").layoutType());
        assertEquals(ProjectLayoutType.UNKNOWN_LEGACY, profile.requireCandidate("legacy-web").layoutType());
        assertTrue(profile.requireCandidate("legacy-web").webRoots().contains("legacy-web/WebRoot"));
        assertTrue(profile.requireCandidate("legacy-web").classpathCandidates().contains(
                "legacy-web/WebRoot/WEB-INF/lib/framework.jar"));
    }

    @Test
    void detectsRootLevelSourceOnlyProject() throws IOException {
        write("src/com/acme/App.java", "package com.acme; class App {}\n");

        WorkspaceLayoutProfile profile = detect();

        assertEquals(ProjectLayoutType.SOURCE_ONLY, profile.requireCandidate(".").layoutType());
        assertTrue(profile.requireCandidate(".").sourceRoots().contains("src"));
    }

    @Test
    void keepsSrcMainWebappOutOfSourceRoots() throws IOException {
        write("webapp/src/main/webapp/WEB-INF/web.xml", "<web-app />\n");
        write("webapp/src/main/webapp/index.jsp", "<html />\n");

        WorkspaceLayoutProfile profile = detect();

        assertEquals(ProjectLayoutType.UNKNOWN_LEGACY, profile.requireCandidate("webapp").layoutType());
        assertTrue(profile.requireCandidate("webapp").sourceRoots().isEmpty());
        assertTrue(profile.requireCandidate("webapp").webRoots().contains("webapp/src/main/webapp"));
    }

    @Test
    void detectsRootLevelLegacyWebRoots() throws IOException {
        write("WebRoot/WEB-INF/web.xml", "<web-app />\n");
        write("WebContent/index.jsp", "<html />\n");

        WorkspaceLayoutProfile profile = detect();

        assertEquals(ProjectLayoutType.UNKNOWN_LEGACY, profile.requireCandidate(".").layoutType());
        assertTrue(profile.requireCandidate(".").webRoots().contains("WebRoot"));
        assertTrue(profile.requireCandidate(".").webRoots().contains("WebContent"));
    }

    @Test
    void detectsRootLevelWebInfAsWebRoot() throws IOException {
        write("WEB-INF/web.xml", "<web-app />\n");

        WorkspaceLayoutProfile profile = detect();

        assertEquals(ProjectLayoutType.UNKNOWN_LEGACY, profile.requireCandidate(".").layoutType());
        assertTrue(profile.requireCandidate(".").webRoots().contains("."));
    }

    @Test
    void keepsSrcMainResourcesOutOfSourceRoots() throws IOException {
        write("resource-only/src/main/resources/application.properties", "app.name=demo\n");

        WorkspaceLayoutProfile profile = detect();

        assertEquals(ProjectLayoutType.UNKNOWN_LEGACY, profile.requireCandidate("resource-only").layoutType());
        assertTrue(profile.requireCandidate("resource-only").sourceRoots().isEmpty());
        assertTrue(profile.requireCandidate("resource-only").resourceRoots().contains("resource-only/src/main/resources"));
    }

    @Test
    void doesNotPromotePlainInventoryFoldersToProjectCandidates() throws IOException {
        write("docs/readme.md", "# notes\n");
        write("samples/data.csv", "id,name\n");

        WorkspaceLayoutProfile profile = detect();

        assertTrue(profile.candidates().isEmpty());
    }

    @Test
    void detectsBytecodeOnlyClasspathCandidates() throws IOException {
        write("lib/app.jar", new byte[] {1, 2, 3});

        WorkspaceLayoutProfile profile = detect();

        assertEquals(ProjectLayoutType.UNKNOWN_LEGACY, profile.requireCandidate(".").layoutType());
        assertTrue(profile.requireCandidate(".").classpathCandidates().contains("lib/app.jar"));
    }

    @Test
    void detectsProjectRootFromCompiledBytecodeOutputs() throws IOException {
        write("app/target/classes/com/acme/App.class", new byte[] {1, 2, 3});
        write("gradle-app/build/classes/java/main/com/acme/App.class", new byte[] {1, 2, 3});
        write("jar-app/target/app.jar", new byte[] {1, 2, 3});

        WorkspaceLayoutProfile profile = detect();

        assertTrue(profile.requireCandidate("app").classpathCandidates()
                .contains("app/target/classes/com/acme/App.class"));
        assertTrue(profile.requireCandidate("gradle-app").classpathCandidates()
                .contains("gradle-app/build/classes/java/main/com/acme/App.class"));
        assertTrue(profile.requireCandidate("jar-app").classpathCandidates()
                .contains("jar-app/target/app.jar"));
    }

    @Test
    void doesNotDetectSourceRootsFromSkippedSourceFiles() throws IOException {
        write("app/src/main/java/Broken.java", new byte[] {(byte) 0xc3, 0x28});

        WorkspaceLayoutProfile profile = detect();

        assertFalse(profile.candidates().stream()
                .anyMatch(candidate -> candidate.rootPath().equals("app")));
    }

    private WorkspaceLayoutProfile detect() throws IOException {
        WorkspaceInventory inventory = WorkspaceInventoryScanner.defaults()
                .scan(ImportRequest.localFolder("ws-layout", tempDir, ImportMode.ASSISTED_IMPORT_REVIEW));
        return WorkspaceLayoutDetector.defaults().detect(inventory);
    }

    private void write(String relativePath, String content) throws IOException {
        write(relativePath, content.getBytes(StandardCharsets.UTF_8));
    }

    private void write(String relativePath, byte[] content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.write(file, content);
    }
}
