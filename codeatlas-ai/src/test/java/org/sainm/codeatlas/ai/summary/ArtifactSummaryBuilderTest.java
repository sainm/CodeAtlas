package org.sainm.codeatlas.ai.summary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.ai.EvidencePack;
import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.EvidenceKey;
import org.sainm.codeatlas.graph.model.FactKey;
import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArtifactSummaryBuilderTest {
    @Test
    void buildsSummariesFromEvidencePack() {
        SymbolId method = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserService", "save", "()V");
        SymbolId mapper = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserMapper", "insert", "()V");
        EvidencePack pack = new EvidencePack(List.of(GraphFact.active(
            new FactKey(method, RelationType.CALLS, mapper, "direct"),
            new EvidenceKey(SourceType.SPOON, "test", "UserService.java", 10, 10, "call"),
            "shop",
            "s1",
            "r1",
            "scope",
            Confidence.CERTAIN,
            SourceType.SPOON
        )));

        List<ArtifactSummary> summaries = new ArtifactSummaryBuilder().build(pack);

        assertEquals(1, summaries.size());
        assertEquals(SummaryKind.METHOD, summaries.getFirst().kind());
        assertTrue(summaries.getFirst().text().contains("CALLS"));
    }
}
