package org.sainm.codeatlas.ai.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sainm.codeatlas.ai.summary.ArtifactSummary;
import org.sainm.codeatlas.ai.summary.SummaryKind;
import org.sainm.codeatlas.graph.model.SymbolId;

class InMemorySemanticSearchIndexTest {
    @Test
    void ranksSummariesByDeterministicLocalEmbeddingSimilarity() {
        EmbeddingProvider provider = new DeterministicHashEmbeddingProvider(64);
        InMemorySemanticSearchIndex index = new InMemorySemanticSearchIndex(provider);
        ArtifactSummary userService = new ArtifactSummary(
            SummaryKind.METHOD,
            SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserService", "saveUser", "()V"),
            "UserService#saveUser",
            "save user profile and account information",
            List.of("e1")
        );
        ArtifactSummary orderService = new ArtifactSummary(
            SummaryKind.METHOD,
            SymbolId.method("shop", "_root", "src/main/java", "com.acme.OrderService", "submitOrder", "()V"),
            "OrderService#submitOrder",
            "submit order and reserve stock",
            List.of("e2")
        );

        index.addAll(List.of(userService, orderService));

        List<SemanticSearchResult> results = index.search("user account save", 2);

        assertEquals(2, results.size());
        assertEquals(userService.symbolId(), results.getFirst().summary().symbolId());
        assertTrue(results.getFirst().score() >= results.getLast().score());
    }
}
