package org.sainm.codeatlas.analyzers.files;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceDirectoryScannerTest {
    @TempDir
    Path tempDir;

    @Test
    void scansOnlyRequestedExtensionsInStableOrder() throws Exception {
        write("src/main/java/B.java", "class B {}\n");
        write("src/main/java/A.java", "class A {}\n");
        write("src/main/webapp/index.jsp", "<html />\n");
        write("README.md", "# ignored\n");

        SourceDirectoryScanner scanner = new SourceDirectoryScanner(new FileHasher());
        List<SourceFileFingerprint> files = scanner.scan(tempDir, Set.of(".java", ".jsp"));

        assertEquals(
            List.of("src/main/java/A.java", "src/main/java/B.java", "src/main/webapp/index.jsp"),
            files.stream().map(SourceFileFingerprint::relativePath).toList()
        );
    }

    private void write(String relativePath, String content) throws Exception {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}

