package org.sainm.codeatlas.symbols;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SymbolKindRegistry {
    private final Map<String, SymbolKind> kinds;

    private SymbolKindRegistry(Map<String, SymbolKind> kinds) {
        this.kinds = Map.copyOf(kinds);
    }

    public static SymbolKindRegistry defaults() {
        Map<String, SymbolKind> kinds = new LinkedHashMap<>();
        for (DefaultSymbolKind defaultKind : DefaultSymbolKind.values()) {
            kinds.put(defaultKind.kind(), defaultKind.toSymbolKind());
        }
        return new SymbolKindRegistry(kinds);
    }

    public SymbolKind require(String kind) {
        SymbolKind symbolKind = kinds.get(kind);
        if (symbolKind == null) {
            throw new IllegalArgumentException("Unregistered symbol kind: " + kind);
        }
        return symbolKind;
    }

    public boolean contains(String kind) {
        return kinds.containsKey(kind);
    }

}
