package org.sainm.codeatlas.analyzers.index;

import org.sainm.codeatlas.graph.model.SymbolId;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public final class FileSymbolIndex {
    private final Map<SymbolId, SymbolLocation> bySymbol = new LinkedHashMap<>();
    private final Map<String, Map<SymbolId, SymbolLocation>> byPath = new TreeMap<>();

    public void add(SymbolLocation location) {
        Objects.requireNonNull(location, "location");
        bySymbol.put(location.symbolId(), location);
        byPath.computeIfAbsent(location.normalizedPath(), ignored -> new LinkedHashMap<>())
            .put(location.symbolId(), location);
    }

    public Optional<SymbolLocation> find(SymbolId symbolId) {
        return Optional.ofNullable(bySymbol.get(symbolId));
    }

    public List<SymbolLocation> symbolsInFile(Path path) {
        return symbolsInFile(path.toString());
    }

    public List<SymbolLocation> symbolsInFile(String path) {
        Map<SymbolId, SymbolLocation> symbols = byPath.get(SymbolLocation.normalizePath(path));
        if (symbols == null) {
            return List.of();
        }
        return symbols.values().stream()
            .sorted(Comparator.comparingInt(SymbolLocation::startLine).thenComparing(location -> location.symbolId().value()))
            .toList();
    }

    public List<ChangedFileCandidates> candidatesForChangedFiles(Collection<Path> changedFiles) {
        Objects.requireNonNull(changedFiles, "changedFiles");
        return changedFiles.stream()
            .map(path -> {
                String normalizedPath = SymbolLocation.normalizePath(path.toString());
                List<SymbolId> candidates = symbolsInFile(normalizedPath).stream()
                    .map(SymbolLocation::symbolId)
                    .toList();
                return new ChangedFileCandidates(normalizedPath, candidates);
            })
            .toList();
    }

    public int symbolCount() {
        return bySymbol.size();
    }

    public int fileCount() {
        return byPath.size();
    }
}
