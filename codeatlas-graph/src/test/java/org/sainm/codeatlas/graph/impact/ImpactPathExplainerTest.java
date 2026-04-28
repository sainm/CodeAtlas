package org.sainm.codeatlas.graph.impact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImpactPathExplainerTest {
    @Test
    void explainsWhyPathIsImpacted() {
        SymbolId action = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserAction", "execute", "()V");
        SymbolId mapper = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserMapper", "insert", "()V");
        ImpactPath path = ImpactPath.fromSteps(
            action,
            mapper,
            List.of(
                new ImpactPathStep(action, null, SourceType.SPOON, Confidence.CERTAIN),
                new ImpactPathStep(mapper, RelationType.CALLS, SourceType.SPOON, Confidence.LIKELY)
            ),
            RiskLevel.MEDIUM,
            "",
            false
        );

        ImpactExplanation explanation = new ImpactPathExplainer().explain(path);

        assertTrue(explanation.summary().contains("is reachable from entrypoint"));
        assertEquals(2, explanation.evidencePath().size());
        assertTrue(explanation.evidencePath().get(1).contains("calls"));
    }
}
