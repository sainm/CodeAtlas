package org.sainm.codeatlas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.EvidenceKey;
import org.sainm.codeatlas.graph.model.FactKey;
import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.model.SymbolKind;
import org.sainm.codeatlas.graph.search.SymbolSearchIndex;
import org.sainm.codeatlas.graph.store.ActiveFact;

class BusinessQueryResolverTest {
    private final NaturalLanguageQueryPlanner planner = new NaturalLanguageQueryPlanner();
    private final BusinessQueryResolver resolver = new BusinessQueryResolver();

    @Test
    void suggestsRequestParameterForBusinessVariableQuestion() {
        QueryPlan plan = planner.plan("userId 从页面到后台去了哪里");

        List<BusinessQueryCandidate> candidates = resolver.resolve(
            "userId 从页面到后台去了哪里",
            plan,
            "shop",
            "_root",
            "s1",
            new SymbolSearchIndex(),
            List.of(),
            5
        );

        assertEquals("VARIABLE_TRACE", plan.intent());
        assertTrue(candidates.stream().anyMatch(candidate -> candidate.symbolId().kind() == SymbolKind.REQUEST_PARAMETER
            && candidate.symbolId().ownerQualifiedName().equals("userId")
            && candidate.suggestedParameter().equals("symbolId")));
    }

    @Test
    void suggestsTableForBusinessTableQuestion() {
        QueryPlan plan = planner.plan("users 表被哪些功能使用");

        List<BusinessQueryCandidate> candidates = resolver.resolve(
            "users 表被哪些功能使用",
            plan,
            "shop",
            "_root",
            "s1",
            new SymbolSearchIndex(),
            List.of(),
            5
        );

        assertEquals("SQL_TABLE_IMPACT", plan.intent());
        assertTrue(candidates.stream().anyMatch(candidate -> candidate.symbolId().kind() == SymbolKind.DB_TABLE
            && candidate.symbolId().ownerQualifiedName().equals("users")));
    }

    @Test
    void usesActiveFactsForCurrentProjectCandidates() {
        SymbolId page = SymbolId.logicalPath(SymbolKind.JSP_PAGE, "shop", "_root", "src/main/webapp", "user/edit.jsp", null);
        SymbolId action = SymbolId.logicalPath(SymbolKind.ACTION_PATH, "shop", "_root", "src/main/webapp", "user/save", null);
        ActiveFact fact = new ActiveFact(
            new FactKey(page, RelationType.SUBMITS_TO, action, "form-action"),
            List.of(new EvidenceKey(SourceType.JSP_FALLBACK, "test", "user/edit.jsp", 3, 3, "form")),
            java.util.Set.of(SourceType.JSP_FALLBACK),
            Confidence.CERTAIN
        );
        QueryPlan plan = planner.plan("user/edit.jsp 页面会调哪里");

        List<BusinessQueryCandidate> candidates = resolver.resolve(
            "user/edit.jsp 页面会调哪里",
            plan,
            "shop",
            "_root",
            "s1",
            new SymbolSearchIndex(),
            List.of(fact),
            5
        );

        assertTrue(candidates.stream().anyMatch(candidate -> candidate.symbolId().equals(page)
            && candidate.reason().contains("当前版本")));
    }
}

