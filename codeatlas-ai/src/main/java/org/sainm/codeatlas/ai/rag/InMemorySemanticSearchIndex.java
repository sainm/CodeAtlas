package org.sainm.codeatlas.ai.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.sainm.codeatlas.ai.summary.ArtifactSummary;

public final class InMemorySemanticSearchIndex {
    private final EmbeddingProvider embeddingProvider;
    private final List<Entry> entries = new ArrayList<>();

    public InMemorySemanticSearchIndex(EmbeddingProvider embeddingProvider) {
        if (embeddingProvider == null) {
            throw new IllegalArgumentException("embeddingProvider is required");
        }
        this.embeddingProvider = embeddingProvider;
    }

    public void addAll(List<ArtifactSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return;
        }
        for (ArtifactSummary summary : summaries) {
            if (summary != null) {
                entries.add(new Entry(summary, embeddingProvider.embed(searchableText(summary))));
            }
        }
    }

    public List<SemanticSearchResult> search(String query, int limit) {
        if (query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }
        EmbeddingVector queryVector = embeddingProvider.embed(query);
        return entries.stream()
            .map(entry -> new SemanticSearchResult(entry.summary, queryVector.cosineSimilarity(entry.vector)))
            .sorted(Comparator.comparingDouble(SemanticSearchResult::score).reversed()
                .thenComparing(result -> result.summary().symbolId().value()))
            .limit(limit)
            .toList();
    }

    private String searchableText(ArtifactSummary summary) {
        return summary.title() + "\n" + summary.text() + "\n" + summary.symbolId().value();
    }

    private record Entry(ArtifactSummary summary, EmbeddingVector vector) {
    }
}
