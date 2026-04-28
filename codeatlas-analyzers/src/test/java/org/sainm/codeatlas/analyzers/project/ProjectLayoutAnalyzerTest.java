package org.sainm.codeatlas.analyzers.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.graph.project.BuildSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectLayoutAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void discoversMavenModulesAndSourceRoots() throws Exception {
        write("pom.xml", """
            <project>
              <modules>
                <module>web</module>
                <module>service</module>
              </modules>
            </project>
            """);
        Files.createDirectories(tempDir.resolve("web/src/main/java"));
        Files.createDirectories(tempDir.resolve("web/src/main/webapp"));
        Files.createDirectories(tempDir.resolve("service/src/main/java"));

        ProjectLayout layout = new ProjectLayoutAnalyzer().analyze("shop", tempDir);

        assertEquals(3, layout.modules().size());
        assertTrue(layout.modules().stream().anyMatch(module -> module.moduleKey().equals("web")
            && module.buildSystem() == BuildSystem.MAVEN
            && module.sourceRoots().stream().anyMatch(root -> root.sourceRootKey().equals("src/main/webapp"))));
    }

    @Test
    void discoversGradleIncludes() throws Exception {
        write("settings.gradle", """
            pluginManagement { repositories { gradlePluginPortal() } }
            include ':codeatlas-graph', 'codeatlas-server'
            """);
        Files.createDirectories(tempDir.resolve("codeatlas-graph/src/main/java"));
        Files.createDirectories(tempDir.resolve("codeatlas-server/src/main/resources"));

        ProjectLayout layout = new ProjectLayoutAnalyzer().analyze("codeatlas", tempDir);

        assertEquals(3, layout.modules().size());
        assertTrue(layout.modules().stream().anyMatch(module -> module.moduleKey().equals(":codeatlas-graph")
            && module.buildSystem() == BuildSystem.GRADLE));
        assertTrue(layout.modules().stream().anyMatch(module -> module.moduleKey().equals("codeatlas-server")
            && module.sourceRoots().stream().anyMatch(root -> root.sourceRootKey().equals("src/main/resources"))));
    }

    private void write(String relativePath, String content) throws Exception {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
