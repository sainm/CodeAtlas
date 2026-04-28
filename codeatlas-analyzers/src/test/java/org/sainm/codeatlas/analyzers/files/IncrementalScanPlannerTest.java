package org.sainm.codeatlas.analyzers.files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IncrementalScanPlannerTest {
    @TempDir
    Path tempDir;

    @Test
    void detectsAddedModifiedRemovedAndUnchangedFiles() throws Exception {
        SourceDirectoryScanner scanner = new SourceDirectoryScanner(new FileHasher());
        Files.writeString(tempDir.resolve("A.java"), "class A {}\n");
        Files.writeString(tempDir.resolve("B.java"), "class B {}\n");
        var before = scanner.scan(tempDir, Set.of(".java"));

        Files.writeString(tempDir.resolve("A.java"), "class A { void a() {} }\n");
        Files.delete(tempDir.resolve("B.java"));
        Files.writeString(tempDir.resolve("C.java"), "class C {}\n");
        var after = scanner.scan(tempDir, Set.of(".java"));

        IncrementalScanDiff diff = new IncrementalScanPlanner().diff(before, after);

        assertTrue(diff.hasChanges());
        assertEquals(3, diff.changedFiles().size());
        assertEquals(FileChangeType.MODIFIED, diff.changedFiles().get(0).type());
        assertEquals(FileChangeType.REMOVED, diff.changedFiles().get(1).type());
        assertEquals(FileChangeType.ADDED, diff.changedFiles().get(2).type());
    }
}
