package org.sainm.codeatlas.ai.rag;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.sainm.codeatlas.ai.summary.ArtifactSummary;
import org.sainm.codeatlas.graph.model.EvidenceKey;
import org.sainm.codeatlas.graph.model.FactKey;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.search.SymbolSearchIndex;
import org.sainm.codeatlas.graph.store.ActiveFact;

public final class HybridRagSearchEngine {
    private final EmbeddingProvider embeddingProvider;

    public HybridRagSearchEngine(EmbeddingProvider embeddingProvider) {
        if (embeddingProvider == null) {
            throw new IllegalArgumentException("embeddingProvider is required");
        }
        this.embeddingProvider = embeddingProvider;
    }

    public List<RagSearchResult> search(
        String query,
        SymbolSearchIndex symbolSearchIndex,
        List<ArtifactSummary> summaries,
        List<ActiveFact> activeFacts,
        int limit
    ) {
        if (query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }
        Map<SymbolId, ArtifactSummary> summaryBySymbol = summaryBySymbol(summaries);
        Map<String, MutableResult> results = new LinkedHashMap<>();
        Set<SymbolId> seeds = new LinkedHashSet<>();

        addExactSymbolMatches(results, seeds, query, symbolSearchIndex, limit);
        addSemanticMatches(results, seeds, query, summaries, limit);
        addGraphNeighbors(results, seeds, summaryBySymbol, activeFacts);

        return results.values().stream()
            .map(MutableResult::toResult)
            .sorted(Comparator.comparingDouble(RagSearchResult::score).reversed()
                .thenComparing(result -> result.symbolId().value()))
            .limit(limit)
            .toList();
    }

    private void addExactSymbolMatches(
        Map<String, MutableResult> results,
        Set<SymbolId> seeds,
        String query,
        SymbolSearchIndex symbolSearchIndex,
        int limit
    ) {
        if (symbolSearchIndex == null) {
            return;
        }
        Set<String> searches = new LinkedHashSet<>();
        searches.add(query);
        searches.addAll(tokens(query));
        for (String search : searches) {
            for (var result : symbolSearchIndex.search(search, Math.max(limit, 20))) {
                seeds.add(result.symbolId());
            merge(results, result.symbolId(), result.displayName(), "", result.score() / 100.0d, RagSearchMatchKind.EXACT_SYMBOL, List.of());
            }
        }
    }

    private void addSemanticMatches(
        Map<String, MutableResult> results,
        Set<SymbolId> seeds,
        String query,
        List<ArtifactSummary> summaries,
        int limit
    ) {
        if (summaries == null || summaries.isEmpty()) {
            return;
        }
        InMemorySemanticSearchIndex semanticIndex = new InMemorySemanticSearchIndex(embeddingProvider);
        semanticIndex.addAll(summaries);
        for (SemanticSearchResult result : semanticIndex.search(query, Math.max(limit, 20))) {
            SymbolId symbolId = result.summary().symbolId();
            seeds.add(symbolId);
            merge(
                results,
                symbolId,
                result.summary().title(),
                result.summary().text(),
                result.score(),
                RagSearchMatchKind.VECTOR,
                result.summary().evidenceKeys()
            );
        }
    }

    private void addGraphNeighbors(
        Map<String, MutableResult> results,
        Set<SymbolId> seeds,
        Map<SymbolId, ArtifactSummary> summaryBySymbol,
        List<ActiveFact> activeFacts
    ) {
        if (seeds.isEmpty() || activeFacts == null || activeFacts.isEmpty()) {
            return;
        }
        for (ActiveFact activeFact : activeFacts) {
            FactKey factKey = activeFact.factKey();
            if (seeds.contains(factKey.source())) {
                addNeighbor(results, factKey.target(), summaryBySymbol, activeFact);
            }
            if (seeds.contains(factKey.target())) {
                addNeighbor(results, factKey.source(), summaryBySymbol, activeFact);
            }
        }
    }

    private void addNeighbor(
        Map<String, MutableResult> results,
        SymbolId symbolId,
        Map<SymbolId, ArtifactSummary> summaryBySymbol,
        ActiveFact activeFact
    ) {
        ArtifactSummary summary = summaryBySymbol.get(symbolId);
        merge(
            results,
            symbolId,
            summary == null ? displayName(symbolId) : summary.title(),
            summary == null ? "" : summary.text(),
            0.42d,
            RagSearchMatchKind.GRAPH_NEIGHBOR,
            activeFact.evidenceKeys().stream().map(EvidenceKey::value).toList()
        );
    }

    private Map<SymbolId, ArtifactSummary> summaryBySymbol(List<ArtifactSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return Map.of();
        }
        Map<SymbolId, ArtifactSummary> bySymbol = new LinkedHashMap<>();
        for (ArtifactSummary summary : summaries) {
            bySymbol.put(summary.symbolId(), summary);
        }
        return bySymbol;
    }

    private void merge(
        Map<String, MutableResult> results,
        SymbolId symbolId,
        String displayName,
        String summary,
        double score,
        RagSearchMatchKind matchKind,
        List<String> evidenceKeys
    ) {
        results.computeIfAbsent(symbolId.value(), ignored -> new MutableResult(symbolId, displayName))
            .merge(summary, score, matchKind, evidenceKeys);
    }

    private String displayName(SymbolId symbolId) {
        if (symbolId.memberName() != null) {
            return symbolId.ownerQualifiedName() + "#" + symbolId.memberName();
        }
        if (symbolId.localId() != null) {
            return symbolId.ownerQualifiedName() + "#" + symbolId.localId();
        }
        return symbolId.ownerQualifiedName() == null ? symbolId.value() : symbolId.ownerQualifiedName();
    }

    private List<String> tokens(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<String> tokens = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < query.length(); i++) {
            char ch = query.charAt(i);
            if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '.' || ch == '#') {
                current.append(ch);
            } else if (!current.isEmpty()) {
                tokens.add(current.toString());
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens.stream()
            .map(value -> value.trim().toLowerCase(Locale.ROOT))
            .filter(value -> value.length() >= 2)
            .distinct()
            .toList();
    }

    private static final class MutableResult {
        private final SymbolId symbolId;
        private final String displayName;
        private final Set<RagSearchMatchKind> matchKinds = new LinkedHashSet<>();
        private final Set<String> evidenceKeys = new LinkedHashSet<>();
        private String summary = "";
        private double score;

        private MutableResult(SymbolId symbolId, String displayName) {
            this.symbolId = symbolId;
            this.displayName = displayName;
        }

        private void merge(String candidateSummary, double candidateScore, RagSearchMatchKind matchKind, List<String> candidateEvidenceKeys) {
            score = Math.max(score, candidateScore);
            if (summary.isBlank() && candidateSummary != null && !candidateSummary.isBlank()) {
                summary = candidateSummary;
            }
            matchKinds.add(matchKind);
            evidenceKeys.addAll(candidateEvidenceKeys);
        }

        private RagSearchResult toResult() {
            return new RagSearchResult(symbolId, displayName, summary, score, matchKinds, List.copyOf(evidenceKeys));
        }
    }
}
