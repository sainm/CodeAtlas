package org.sainm.codeatlas.analyzers.files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileHasherTest {
    @TempDir
    Path tempDir;

    @Test
    void fingerprintsFilesRelativeToRoot() throws Exception {
        Path file = tempDir.resolve("src/main/java/com/acme/User.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "class User {}\n");

        SourceFileFingerprint fingerprint = new FileHasher().fingerprint(tempDir, file);

        assertEquals("src/main/java/com/acme/User.java", fingerprint.relativePath());
        assertEquals("src/main/java/com/acme/user.java", fingerprint.normalizedPathLower());
        assertEquals(14, fingerprint.size());
        assertEquals(64, fingerprint.sha256().length());
    }

    @Test
    void rejectsFilesOutsideRoot() throws Exception {
        Path outside = Files.createTempFile("codeatlas", ".java");
        try {
            assertThrows(IllegalArgumentException.class, () -> new FileHasher().fingerprint(tempDir, outside));
        } finally {
            Files.deleteIfExists(outside);
        }
    }
}

