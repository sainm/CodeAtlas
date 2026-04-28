package org.sainm.codeatlas.analyzers.git;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.sainm.codeatlas.analyzers.index.FileSymbolIndex;
import org.sainm.codeatlas.analyzers.index.SymbolLocation;
import org.sainm.codeatlas.graph.model.SymbolId;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UnifiedDiffParserTest {
    @Test
    void parsesChangedFilesFromUnifiedDiff() {
        String diff = """
            diff --git a/src/main/java/com/acme/UserService.java b/src/main/java/com/acme/UserService.java
            --- a/src/main/java/com/acme/UserService.java
            +++ b/src/main/java/com/acme/UserService.java
            @@ -1 +1 @@
            -old
            +new
            diff --git a/old.jsp b/new.jsp
            --- a/old.jsp
            +++ b/new.jsp
            """;

        var files = new UnifiedDiffParser().parseChangedFiles(diff);

        assertEquals(2, files.size());
        assertEquals("src/main/java/com/acme/UserService.java", files.getFirst().effectivePath());
        assertEquals("new.jsp", files.get(1).effectivePath());
    }

    @Test
    void resolvesChangedFilesToSymbolCandidates() {
        SymbolId service = SymbolId.classSymbol("shop", "_root", "src/main/java", "com.acme.UserService");
        FileSymbolIndex index = new FileSymbolIndex();
        index.add(SymbolLocation.of(service, Path.of("src/main/java/com/acme/UserService.java"), 1, 50));
        String diff = """
            diff --git a/src/main/java/com/acme/UserService.java b/src/main/java/com/acme/UserService.java
            --- a/src/main/java/com/acme/UserService.java
            +++ b/src/main/java/com/acme/UserService.java
            """;

        var candidates = new DiffSymbolCandidateResolver().resolve(diff, index);

        assertEquals(1, candidates.size());
        assertEquals(service, candidates.getFirst().candidates().getFirst());
    }
}
