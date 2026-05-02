package org.sainm.codeatlas.symbols;

public record SymbolKind(String kind, IdentityType identityType) {
    public SymbolKind {
        if (kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("Symbol kind is required");
        }
        if (identityType == null) {
            throw new IllegalArgumentException("Identity type is required");
        }
    }
}
