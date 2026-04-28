package org.sainm.codeatlas.analyzers.git;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.sainm.codeatlas.analyzers.index.FileSymbolIndex;
import org.sainm.codeatlas.analyzers.index.SymbolLocation;
import org.sainm.codeatlas.graph.model.SymbolId;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitChangedSymbolResolverTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesJGitDiffToCandidateSymbols() throws Exception {
        String firstCommit;
        String secondCommit;
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            Files.createDirectories(tempDir.resolve("src/main/java/com/acme"));
            Files.writeString(tempDir.resolve("src/main/java/com/acme/UserService.java"), "class UserService {}\n");
            git.add().addFilepattern("src/main/java/com/acme/UserService.java").call();
            firstCommit = git.commit().setMessage("initial").setAuthor("CodeAtlas", "codeatlas@example.com").call().getId().name();

            Files.writeString(tempDir.resolve("src/main/java/com/acme/UserService.java"), "class UserService { void save() {} }\n");
            git.add().addFilepattern("src/main/java/com/acme/UserService.java").call();
            secondCommit = git.commit().setMessage("change").setAuthor("CodeAtlas", "codeatlas@example.com").call().getId().name();
        }

        SymbolId service = SymbolId.classSymbol("shop", "_root", "src/main/java", "com.acme.UserService");
        FileSymbolIndex index = new FileSymbolIndex();
        index.add(SymbolLocation.of(service, Path.of("src/main/java/com/acme/UserService.java"), 1, 50));

        var candidates = new GitChangedSymbolResolver(new JGitRepositoryReader()).resolve(tempDir, firstCommit, secondCommit, index);

        assertEquals(1, candidates.size());
        assertEquals(service, candidates.getFirst().candidates().getFirst());
    }
}
