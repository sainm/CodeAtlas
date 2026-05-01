package org.sainm.codeatlas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NaturalLanguageQueryPlannerTest {
    private final NaturalLanguageQueryPlanner planner = new NaturalLanguageQueryPlanner();

    @Test
    void routesVariableTraceQuestion() {
        QueryPlan plan = planner.plan("where does request parameter userId come from and where does it go");

        assertEquals("VARIABLE_TRACE", plan.intent());
        assertTrue(plan.relationTypes().contains("WRITES_PARAM"));
        assertTrue(plan.relationTypes().contains("READS_PARAM"));
    }

    @Test
    void routesProjectOverviewQuestion() {
        QueryPlan plan = planner.plan("项目总览和分析状态");

        assertEquals("PROJECT_OVERVIEW", plan.intent());
        assertEquals("/api/project/overview", plan.endpoint());
        assertEquals("PROJECT_OVERVIEW_VIEW", plan.resultView());
    }

    @Test
    void routesJspFlowQuestion() {
        QueryPlan plan = planner.plan("JSP form submit to which Struts action backend flow");

        assertEquals("JSP_BACKEND_FLOW", plan.intent());
        assertTrue(plan.relationTypes().contains("INCLUDES"));
        assertTrue(plan.relationTypes().contains("SUBMITS_TO"));
    }

    @Test
    void routesStrutsDispatchQuestionToJspFlow() {
        QueryPlan plan = planner.plan("Struts LookupDispatchAction button.search forward chain");

        assertEquals("JSP_BACKEND_FLOW", plan.intent());
        assertTrue(plan.relationTypes().contains("ROUTES_TO"));
        assertTrue(plan.relationTypes().contains("FORWARDS_TO"));
    }

    @Test
    void routesExplicitActionCallerQuestionToCallGraph() {
        QueryPlan plan = planner.plan("who calls action /user/save");

        assertEquals("CALL_GRAPH", plan.intent());
        assertTrue(plan.relationTypes().contains("CALLS"));
    }

    @Test
    void routesExplicitActionImpactQuestionToImpactAnalysis() {
        QueryPlan plan = planner.plan("changed action /user/save impact");

        assertEquals("IMPACT_ANALYSIS", plan.intent());
        assertTrue(plan.relationTypes().contains("ROUTES_TO"));
    }

    @Test
    void routesImpactQuestion() {
        QueryPlan plan = planner.plan("changed UserService risk and affected entrypoints");

        assertEquals("IMPACT_ANALYSIS", plan.intent());
        assertTrue(plan.relationTypes().contains("CALLS"));
        assertTrue(plan.relationTypes().contains("INCLUDES"));
    }

    @Test
    void routesDiffImpactQuestion() {
        QueryPlan plan = planner.plan("analyze this git diff impact");

        assertEquals("DIFF_IMPACT_ANALYSIS", plan.intent());
        assertEquals("/api/impact/analyze-diff", plan.endpoint());
        assertTrue(plan.requiredParameters().contains("diffText"));
    }

    @Test
    void routesSqlQuestion() {
        QueryPlan plan = planner.plan("users table column affects which mapper");

        assertEquals("SQL_TABLE_IMPACT", plan.intent());
        assertTrue(plan.relationTypes().contains("READS_TABLE"));
        assertTrue(plan.relationTypes().contains("WRITES_TABLE"));
    }

    @Test
    void routesCodeQuestionToRagSemanticSearch() {
        QueryPlan plan = planner.plan("explain UserService and find related code");

        assertEquals("RAG_SEMANTIC_SEARCH", plan.intent());
        assertEquals("/api/rag/answer-draft", plan.endpoint());
        assertTrue(plan.requiredParameters().contains("q"));
        assertEquals("RAG_SEARCH_VIEW", plan.resultView());
    }
}
