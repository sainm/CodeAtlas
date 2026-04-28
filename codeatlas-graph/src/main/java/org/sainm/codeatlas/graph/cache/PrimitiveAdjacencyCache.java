package org.sainm.codeatlas.graph.cache;

import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.store.ActiveFact;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PrimitiveAdjacencyCache {
    private final AdjacencyCacheKey key;
    private final Map<String, Integer> nodeIds = new HashMap<>();
    private final List<String> symbolsById = new ArrayList<>();
    private final Map<Integer, List<Integer>> outgoing = new HashMap<>();
    private final Map<Integer, List<Integer>> incoming = new HashMap<>();
    private long hits;
    private long misses;
    private int edgeCount;

    private PrimitiveAdjacencyCache(AdjacencyCacheKey key) {
        this.key = key;
    }

    public static PrimitiveAdjacencyCache build(AdjacencyCacheKey key, List<ActiveFact> activeFacts) {
        PrimitiveAdjacencyCache cache = new PrimitiveAdjacencyCache(key);
        activeFacts.stream()
            .filter(fact -> key.relationGroup().relationTypes().contains(fact.factKey().relationType()))
            .sorted(Comparator.comparing(fact -> fact.factKey().value()))
            .forEach(cache::add);
        cache.sort();
        return cache;
    }

    public AdjacencyCacheKey key() {
        return key;
    }

    public List<String> callees(SymbolId caller) {
        return neighbors(caller.value(), outgoing);
    }

    public List<String> callers(SymbolId callee) {
        return neighbors(callee.value(), incoming);
    }

    public List<List<String>> bfs(SymbolId start, int maxDepth, int maxPaths) {
        Integer startId = nodeIds.get(start.value());
        if (startId == null) {
            misses++;
            return List.of();
        }
        hits++;
        List<List<Integer>> queue = new ArrayList<>();
        queue.add(List.of(startId));
        List<List<String>> paths = new ArrayList<>();
        for (int cursor = 0; cursor < queue.size() && paths.size() < maxPaths; cursor++) {
            List<Integer> path = queue.get(cursor);
            int last = path.getLast();
            if (path.size() > 1) {
                paths.add(path.stream().map(symbolsById::get).toList());
            }
            if (path.size() - 1 >= maxDepth) {
                continue;
            }
            Set<Integer> visited = new HashSet<>(path);
            for (int next : outgoing.getOrDefault(last, List.of())) {
                if (visited.contains(next)) {
                    continue;
                }
                List<Integer> extended = new ArrayList<>(path);
                extended.add(next);
                queue.add(extended);
            }
        }
        return paths;
    }

    public AdjacencyCacheStats stats() {
        return new AdjacencyCacheStats(symbolsById.size(), edgeCount, estimateBytes(), hits, misses);
    }

    private List<String> neighbors(String symbolId, Map<Integer, List<Integer>> adjacency) {
        Integer id = nodeIds.get(symbolId);
        if (id == null) {
            misses++;
            return List.of();
        }
        hits++;
        return adjacency.getOrDefault(id, List.of()).stream()
            .map(symbolsById::get)
            .toList();
    }

    private void add(ActiveFact fact) {
        int source = id(fact.factKey().source().value());
        int target = id(fact.factKey().target().value());
        outgoing.computeIfAbsent(source, ignored -> new ArrayList<>()).add(target);
        incoming.computeIfAbsent(target, ignored -> new ArrayList<>()).add(source);
        edgeCount++;
    }

    private int id(String symbolId) {
        return nodeIds.computeIfAbsent(symbolId, key -> {
            symbolsById.add(key);
            return symbolsById.size() - 1;
        });
    }

    private void sort() {
        outgoing.values().forEach(list -> list.sort(Integer::compareTo));
        incoming.values().forEach(list -> list.sort(Integer::compareTo));
    }

    private long estimateBytes() {
        return 64L + symbolsById.size() * 96L + edgeCount * 16L;
    }
}
