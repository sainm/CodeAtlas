package org.sainm.codeatlas.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sainm.codeatlas.facts.Confidence;
import org.sainm.codeatlas.facts.CurrentFactReport;
import org.sainm.codeatlas.facts.FactRecord;
import org.sainm.codeatlas.facts.SourceType;

class PrimitiveAdjacencyCacheTest {
    @Test
    void buildsCallerAndCalleeAdjacencyFromActiveCallFacts() {
        String controller = method("com.acme.UserController", "show", "()V");
        String service = method("com.acme.UserService", "load", "()V");
        String repository = method("com.acme.UserRepository", "find", "()V");
        CurrentFactReport report = CurrentFactReport.from("shop", List.of(
                call(controller, service),
                call(service, repository),
                fact(service, "db-table://shop/mainDs/public/users", "READS_TABLE")));

        PrimitiveAdjacencyCache cache = PrimitiveAdjacencyCache.from(report);

        assertEquals(List.of(service), cache.callees(controller));
        assertEquals(List.of(controller), cache.callers(service));
        assertEquals(List.of(repository), cache.callees(service));
        assertEquals(2, cache.edgeCount());
    }

    private static FactRecord call(String source, String target) {
        return fact(source, target, "CALLS");
    }

    private static FactRecord fact(String source, String target, String relation) {
        return FactRecord.create(
                List.of("src/main/java", "src/main/resources"),
                source,
                target,
                relation,
                "test",
                "shop",
                "snapshot-1",
                "analysis-1",
                "scope-1",
                "graph-test",
                "src/main/java",
                "evidence-1",
                Confidence.CERTAIN,
                100,
                SourceType.SPOON);
    }

    private static String method(String owner, String method, String signature) {
        return "method://shop/_root/src/main/java/" + owner + "#" + method + signature;
    }
}
