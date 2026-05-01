package org.sainm.codeatlas.ai.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sainm.codeatlas.ai.summary.ArtifactSummary;
import org.sainm.codeatlas.ai.summary.SummaryKind;
import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.EvidenceKey;
import org.sainm.codeatlas.graph.model.FactKey;
import org.sainm.codeatlas.graph.model.GraphNodeFactory;
import org.sainm.codeatlas.graph.model.NodeRole;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.search.SymbolSearchIndex;
import org.sainm.codeatlas.graph.store.ActiveFact;

class HybridRagSearchEngineTest {
    @Test
    void combinesExactSymbolSearchSemanticRecallAndGraphExpansion() {
        SymbolId action = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserAction", "execute", "()V");
        SymbolId service = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserService", "saveUser", "()V");
        SymbolId mapper = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserMapper", "insert", "()V");
        SymbolSearchIndex symbolIndex = new SymbolSearchIndex();
        symbolIndex.add(GraphNodeFactory.methodNode(action, NodeRole.STRUTS_ACTION));
        symbolIndex.add(GraphNodeFactory.methodNode(service, NodeRole.SERVICE));
        List<ArtifactSummary> summaries = List.of(
            new ArtifactSummary(SummaryKind.METHOD, service, "UserService#saveUser", "save user profile and account", List.of("e-service")),
            new ArtifactSummary(SummaryKind.METHOD, mapper, "UserMapper#insert", "insert user row into database", List.of("e-mapper"))
        );
        List<ActiveFact> activeFacts = List.of(new ActiveFact(
            new FactKey(service, RelationType.CALLS, mapper, "direct"),
            List.of(new EvidenceKey(SourceType.SPOON, "call", "UserService.java", 12, 12, "mapper.insert(user)")),
            Set.of(SourceType.SPOON),
            Confidence.CERTAIN
        ));
        HybridRagSearchEngine engine = new HybridRagSearchEngine(new DeterministicHashEmbeddingProvider(64));

        List<RagSearchResult> results = engine.search("UserService save account", symbolIndex, summaries, activeFacts, 5);

        assertEquals(service, results.getFirst().symbolId());
        assertTrue(results.getFirst().matchKinds().contains(RagSearchMatchKind.EXACT_SYMBOL));
        assertTrue(results.getFirst().matchKinds().contains(RagSearchMatchKind.VECTOR));
        RagSearchResult mapperResult = results.stream()
            .filter(result -> result.symbolId().equals(mapper))
            .findFirst()
            .orElseThrow();
        assertTrue(mapperResult.matchKinds().contains(RagSearchMatchKind.GRAPH_NEIGHBOR));
        assertTrue(mapperResult.evidenceKeys().contains("SPOON|call|UserService.java|12|12|mapper.insert(user)"));
    }
}
