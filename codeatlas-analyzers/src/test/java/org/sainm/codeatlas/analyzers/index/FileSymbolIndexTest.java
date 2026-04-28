package org.sainm.codeatlas.analyzers.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.graph.model.SymbolId;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class FileSymbolIndexTest {
    @Test
    void indexesSymbolsByNormalizedFilePath() {
        FileSymbolIndex index = new FileSymbolIndex();
        SymbolId clazz = SymbolId.classSymbol("shop", "_root", "src/main/java", "com.acme.UserService");
        SymbolId method = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserService", "save", "()V");

        index.add(SymbolLocation.of(method, Path.of("src\\main\\java\\com\\acme\\UserService.java"), 12, 18));
        index.add(SymbolLocation.of(clazz, Path.of("src/main/java/com/acme/UserService.java"), 1, 30));

        List<SymbolLocation> symbols = index.symbolsInFile("src/main/java/com/acme/UserService.java");

        assertEquals(2, index.symbolCount());
        assertEquals(1, index.fileCount());
        assertEquals(clazz, symbols.get(0).symbolId());
        assertEquals(method, symbols.get(1).symbolId());
    }

    @Test
    void mapsChangedFilesToCandidateSymbols() {
        FileSymbolIndex index = new FileSymbolIndex();
        SymbolId mapper = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserMapper", "select", "()V");
        index.add(SymbolLocation.of(mapper, Path.of("src/main/java/com/acme/UserMapper.java"), 5, 7));

        List<ChangedFileCandidates> candidates = index.candidatesForChangedFiles(List.of(
            Path.of("src\\main\\java\\com\\acme\\UserMapper.java"),
            Path.of("README.md")
        ));

        assertEquals("src/main/java/com/acme/UserMapper.java", candidates.get(0).normalizedPath());
        assertEquals(List.of(mapper), candidates.get(0).candidates());
        assertTrue(candidates.get(1).candidates().isEmpty());
    }
}
