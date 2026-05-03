package org.sainm.codeatlas.analyzers.source;

public record JavaMergedSymbol(
        JavaMergedSymbolKind kind,
        String stableKey,
        boolean sourcePresent,
        boolean jvmPresent) {
    public JavaMergedSymbol {
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        if (stableKey == null || stableKey.isBlank()) {
            throw new IllegalArgumentException("stableKey is required");
        }
    }

    public boolean sourceOnly() {
        return sourcePresent && !jvmPresent;
    }

    public boolean jvmOnly() {
        return jvmPresent && !sourcePresent;
    }
}
