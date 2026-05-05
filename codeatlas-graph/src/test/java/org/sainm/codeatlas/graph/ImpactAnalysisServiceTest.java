package org.sainm.codeatlas.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sainm.codeatlas.facts.Confidence;
import org.sainm.codeatlas.facts.FactRecord;
import org.sainm.codeatlas.facts.InMemoryFactStore;
import org.sainm.codeatlas.facts.SourceType;

class ImpactAnalysisServiceTest {
    @Test
    void analyzeDiffBuildsImpactReportWithCallerAndDownstreamPaths() {
        String changed = method("A", "target");
        String caller1 = method("B", "caller1");
        String caller2 = method("C", "caller2");
        String downstream1 = "sql-statement://shop/_root/src/main/resources/mapper.xml#find";

        InMemoryFactStore store = InMemoryFactStore.defaults();
        store.insertAll(List.of(
                fact(caller1, changed, "CALLS"),
                fact(caller2, changed, "CALLS"),
                fact(changed, downstream1, "BINDS_TO")));

        ImpactAnalysisService service = ImpactAnalysisService.using(store);
        FastImpactReport report = service.analyzeDiff("shop", "snap-1", List.of(changed), 3, 10);

        assertEquals("shop", report.projectId());
        assertEquals(List.of(changed), report.changedSymbols());
        assertFalse(report.paths().isEmpty());
        assertFalse(report.affectedSymbols().isEmpty());
        assertFalse(report.truncated());
    }

    @Test
    void tracesVariableCombined() {
        String paramSlotId = "param-slot://shop/_root/src/main/java/A#m()V:param[0:Ljava/lang/String;";
        String nextSlotId = "param-slot://shop/_root/src/main/java/B#n(Ljava/lang/String;)V:param[0:Ljava/lang/String;";

        InMemoryFactStore store = InMemoryFactStore.defaults();
        store.insert(fact(paramSlotId, nextSlotId, "PASSES_PARAM"));

        ImpactAnalysisService service = ImpactAnalysisService.using(store);
        VariableTraceReport report = service.traceVariable("shop", "snap-1", paramSlotId, 5, 10);

        assertEquals("combined", report.mode());
        assertEquals(paramSlotId, report.startIdentityId());
        assertTrue(report.combinedPaths() != null);
    }

    @Test
    void findsWebBackendFlows() {
        String jspPage = "jsp-page://shop/web-root/user/edit.jsp";
        String actionPath = "action-path://shop/web-root/user/save";
        String method = method("UserAction", "save");
        String sql = "sql-statement://shop/_root/src/main/resources/mapper/UserMapper.xml#update";

        InMemoryFactStore store = InMemoryFactStore.defaults();
        store.insertAll(List.of(
                fact(jspPage, actionPath, "SUBMITS_TO"),
                fact(actionPath, method, "ROUTES_TO"),
                fact(method, sql, "BINDS_TO")));

        ImpactAnalysisService service = ImpactAnalysisService.using(store);
        WebBackendFlowSearchResult result = service.findWebBackendFlow("shop", "snap-1", jspPage, 5, 10);

        assertEquals(jspPage, result.sourceIdentityId());
        assertFalse(result.backendFlowPaths().isEmpty());
    }

    private static String method(String owner, String name) {
        return "method://shop/_root/src/main/java/com/acme/" + owner + "#" + name + "()V";
    }

    private static FactRecord fact(String source, String target, String relation) {
        return FactRecord.create(
                List.of("src/main/java"),
                source,
                target,
                relation,
                "direct",
                "shop",
                "snap-1",
                "analysis-1",
                "scope-1",
                "spoon",
                "src/main/java",
                "evidence-1",
                Confidence.CERTAIN,
                100,
                SourceType.SPOON);
    }
}
