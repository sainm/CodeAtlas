package org.sainm.codeatlas.analyzers.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JGitDiffReaderTest {
    @TempDir
    Path tempDir;

    @Test
    void readsBranchCommitsChangedFilesAndHunks() throws IOException, GitAPIException {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            write("src/main/java/com/acme/User.java", """
                    package com.acme;

                    class User {
                        String name;
                    }
                    """);
            git.add().addFilepattern(".").call();
            String baseCommit = git.commit().setMessage("base").call().getName();

            write("src/main/java/com/acme/User.java", """
                    package com.acme;

                    class User {
                        String displayName;
                    }
                    """);
            write("src/main/resources/com/acme/UserMapper.xml", "<mapper namespace=\"com.acme.UserMapper\"/>\n");
            git.add().addFilepattern(".").call();
            String headCommit = git.commit().setMessage("head").call().getName();

            GitDiffDetails details = JGitDiffReader.defaults().read(tempDir, baseCommit, headCommit);

            assertFalse(details.branchName().isBlank());
            assertEquals(baseCommit, details.baseCommitId());
            assertEquals(headCommit, details.headCommitId());
            assertEquals(List.of(
                    "src/main/java/com/acme/User.java",
                    "src/main/resources/com/acme/UserMapper.xml"), details.summary().changedPaths());
            GitChangedFile javaFile = details.changedFiles().stream()
                    .filter(file -> file.effectivePath().equals("src/main/java/com/acme/User.java"))
                    .findFirst()
                    .orElseThrow();
            assertEquals("MODIFY", javaFile.changeType());
            assertTrue(javaFile.hunks().stream()
                    .anyMatch(hunk -> hunk.oldStartLine() > 0 && hunk.newStartLine() > 0));
        }
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
