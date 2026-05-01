package org.sainm.codeatlas.graph.model;

public final class SymbolIdNormalizer {
    private SymbolIdNormalizer() {
    }

    public static SymbolId normalize(SymbolId symbolId) {
        if (symbolId == null) {
            throw new IllegalArgumentException("symbolId is required");
        }
        return SymbolIdParser.parse(symbolId.value());
    }

    public static String canonicalValue(SymbolId symbolId) {
        return normalize(symbolId).value();
    }

    public static boolean isCanonical(SymbolId symbolId) {
        return symbolId != null && symbolId.value().equals(canonicalValue(symbolId));
    }
}
