package org.sainm.codeatlas.analyzers.source;

import java.util.List;

public record JavaSymbolOriginMergeResult(List<JavaMergedSymbol> symbols) {
    public JavaSymbolOriginMergeResult {
        symbols = List.copyOf(symbols == null ? List.of() : symbols);
    }

    public JavaMergedSymbol requireSymbol(JavaMergedSymbolKind kind, String stableKey) {
        return symbols.stream()
                .filter(symbol -> symbol.kind() == kind && symbol.stableKey().equals(stableKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No merged Java symbol for " + kind + ":" + stableKey));
    }
}
