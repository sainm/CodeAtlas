package org.sainm.codeatlas.analyzers.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JGitRepositoryReaderTest {
    @TempDir
    Path tempDir;

    @Test
    void readsRepositoryInfoAndCommitDiff() throws Exception {
        String firstCommit;
        String secondCommit;
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            Files.writeString(tempDir.resolve("UserService.java"), "class UserService {}\n");
            git.add().addFilepattern("UserService.java").call();
            firstCommit = git.commit().setMessage("initial").setAuthor("CodeAtlas", "codeatlas@example.com").call().getId().name();

            Files.writeString(tempDir.resolve("UserService.java"), "class UserService { void save() {} }\n");
            git.add().addFilepattern("UserService.java").call();
            secondCommit = git.commit().setMessage("change").setAuthor("CodeAtlas", "codeatlas@example.com").call().getId().name();
        }

        JGitRepositoryReader reader = new JGitRepositoryReader();
        GitRepositoryInfo info = reader.info(tempDir);
        GitDiffResult diff = reader.diff(tempDir, firstCommit, secondCommit);

        assertEquals(secondCommit, info.headCommit());
        assertEquals(1, diff.changedFiles().size());
        assertEquals("UserService.java", diff.changedFiles().getFirst().effectivePath());
        assertTrue(diff.unifiedDiff().contains("+class UserService { void save() {} }"));
    }
}
