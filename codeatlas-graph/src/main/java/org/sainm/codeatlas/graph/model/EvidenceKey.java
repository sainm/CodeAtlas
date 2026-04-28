package org.sainm.codeatlas.graph.model;

import java.util.Objects;

public record EvidenceKey(
    SourceType sourceType,
    String analyzer,
    String path,
    int lineStart,
    int lineEnd,
    String localPath
) {
    public EvidenceKey {
        Objects.requireNonNull(sourceType, "sourceType");
        analyzer = require(analyzer, "analyzer");
        path = SymbolId.normalizePath(require(path, "path"));
        if (lineStart < 0 || lineEnd < 0) {
            throw new IllegalArgumentException("line numbers must be non-negative");
        }
        if (lineEnd > 0 && lineStart > lineEnd) {
            throw new IllegalArgumentException("lineStart must be <= lineEnd");
        }
        localPath = localPath == null ? "" : localPath.trim();
    }

    public String value() {
        return sourceType.name() + "|"
            + analyzer + "|"
            + path + "|"
            + lineStart + "|"
            + lineEnd + "|"
            + localPath;
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}

