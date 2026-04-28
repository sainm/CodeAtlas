package org.sainm.codeatlas.analyzers.index;

import org.sainm.codeatlas.graph.model.SymbolId;
import java.nio.file.Path;
import java.util.Objects;

public record SymbolLocation(
    SymbolId symbolId,
    String normalizedPath,
    int startLine,
    int endLine
) {
    public SymbolLocation {
        Objects.requireNonNull(symbolId, "symbolId");
        normalizedPath = normalizePath(normalizedPath);
        if (startLine < 1) {
            throw new IllegalArgumentException("startLine must be positive");
        }
        if (endLine < startLine) {
            throw new IllegalArgumentException("endLine must be greater than or equal to startLine");
        }
    }

    public static SymbolLocation of(SymbolId symbolId, Path path, int startLine, int endLine) {
        return new SymbolLocation(symbolId, path.toString(), startLine, endLine);
    }

    static String normalizePath(String path) {
        Objects.requireNonNull(path, "path");
        String normalized = path.trim().replace('\\', '/');
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }
}
