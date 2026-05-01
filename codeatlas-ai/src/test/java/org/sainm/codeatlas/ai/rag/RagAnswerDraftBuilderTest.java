package org.sainm.codeatlas.ai.rag;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sainm.codeatlas.graph.model.SymbolId;

class RagAnswerDraftBuilderTest {
    @Test
    void buildsBusinessReadableAnswerFromRagResults() {
        SymbolId service = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserService", "saveUser", "()V");
        RagSearchResult result = new RagSearchResult(
            service,
            "UserService#saveUser",
            "Static evidence count: 2; relations: CALLS, WRITES_TABLE.",
            0.9d,
            Set.of(RagSearchMatchKind.EXACT_SYMBOL, RagSearchMatchKind.VECTOR),
            List.of("SPOON|test|UserService.java|22|22|service.save(user)")
        );

        String answer = new RagAnswerDraftBuilder().build("explain user save", List.of(result));

        assertTrue(answer.contains("explain user save"));
        assertTrue(answer.contains("UserService#saveUser"));
        assertTrue(answer.contains("EXACT_SYMBOL"));
        assertTrue(answer.contains("UserService.java"));
        assertTrue(answer.contains("Static evidence count"));
    }
}
