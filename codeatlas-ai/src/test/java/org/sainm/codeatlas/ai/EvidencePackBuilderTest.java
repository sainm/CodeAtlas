package org.sainm.codeatlas.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.EvidenceKey;
import org.sainm.codeatlas.graph.model.FactKey;
import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EvidencePackBuilderTest {
    @Test
    void filtersAndLimitsEvidenceFacts() {
        SymbolId a = SymbolId.method("shop", "_root", "src/main/java", "com.acme.A", "a", "()V");
        SymbolId b = SymbolId.method("shop", "_root", "src/main/java", "com.acme.B", "b", "()V");
        List<GraphFact> facts = List.of(fact(a, b, SourceType.XML), fact(a, b, SourceType.SPOON));

        EvidencePack pack = new EvidencePackBuilder().build(facts, Set.of(SourceType.SPOON), 10);

        assertEquals(1, pack.evidenceCount());
        assertEquals(SourceType.SPOON, pack.facts().getFirst().sourceType());
    }

    private GraphFact fact(SymbolId source, SymbolId target, SourceType sourceType) {
        return GraphFact.active(
            new FactKey(source, RelationType.CALLS, target, "direct"),
            new EvidenceKey(sourceType, "test", sourceType + ".java", 1, 1, "call"),
            "shop",
            "s1",
            "r1",
            "scope",
            Confidence.CERTAIN,
            sourceType
        );
    }
}
