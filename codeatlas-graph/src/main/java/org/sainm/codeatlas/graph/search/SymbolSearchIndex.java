package org.sainm.codeatlas.graph.search;

import org.sainm.codeatlas.graph.model.GraphNode;
import org.sainm.codeatlas.graph.model.SymbolId;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SymbolSearchIndex {
    private final Map<String, GraphNode> nodes = new LinkedHashMap<>();

    public void add(GraphNode node) {
        nodes.put(node.symbolId().value(), node);
    }

    public void addAll(Collection<GraphNode> graphNodes) {
        graphNodes.forEach(this::add);
    }

    public List<SymbolSearchResult> search(String query, int limit) {
        String normalized = normalize(query);
        if (normalized.isBlank()) {
            return List.of();
        }
        return nodes.values().stream()
            .map(node -> score(node, normalized))
            .filter(result -> result.score() > 0)
            .sorted(Comparator.comparingInt(SymbolSearchResult::score).reversed()
                .thenComparing(result -> result.symbolId().value()))
            .limit(Math.max(1, limit))
            .toList();
    }

    public int size() {
        return nodes.size();
    }

    private SymbolSearchResult score(GraphNode node, String query) {
        SymbolId symbol = node.symbolId();
        String value = normalize(symbol.value());
        String owner = normalize(symbol.ownerQualifiedName());
        String member = normalize(symbol.memberName());
        String localId = normalize(symbol.localId());
        String displayName = displayName(node);

        int score = 0;
        if (value.equals(query)) {
            score = 100;
        } else if (member.equals(query)) {
            score = 90;
        } else if (owner.endsWith("." + query) || owner.equals(query)) {
            score = 85;
        } else if (localId.equals(query)) {
            score = 80;
        } else if (value.contains(query)) {
            score = 60;
        } else if (owner.contains(query) || member.contains(query) || localId.contains(query)) {
            score = 50;
        }
        return new SymbolSearchResult(symbol, symbol.kind(), displayName, score);
    }

    private String displayName(GraphNode node) {
        String configured = node.properties().get("displayName");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        SymbolId symbol = node.symbolId();
        if (symbol.memberName() != null) {
            return symbol.ownerQualifiedName() + "#" + symbol.memberName();
        }
        if (symbol.ownerQualifiedName() != null) {
            return symbol.ownerQualifiedName();
        }
        if (symbol.localId() != null) {
            return symbol.localId();
        }
        return symbol.value();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
