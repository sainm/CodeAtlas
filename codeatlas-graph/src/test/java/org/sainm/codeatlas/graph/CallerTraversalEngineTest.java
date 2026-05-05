package org.sainm.codeatlas.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sainm.codeatlas.facts.Confidence;
import org.sainm.codeatlas.facts.CurrentFactReport;
import org.sainm.codeatlas.facts.FactRecord;
import org.sainm.codeatlas.facts.SourceType;

class CallerTraversalEngineTest {
    @Test
    void traversesCallersFromChangedMethod() {
        String controller = method("com.acme.UserController", "show", "()V");
        String service = method("com.acme.UserService", "load", "()V");
        String repository = method("com.acme.UserRepository", "find", "()V");
        CurrentFactReport report = CurrentFactReport.from("shop", List.of(
                call(controller, service),
                call(service, repository)));

        CallerTraversalResult result = CallerTraversalEngine.defaults().findCallers(report, repository, 4, 10);

        assertEquals(repository, result.changedMethodId());
        assertFalse(result.truncated());
        assertTrue(result.callerPaths().stream()
                .anyMatch(path -> path.identityIds().equals(List.of(repository, service))));
        assertTrue(result.callerPaths().stream()
                .anyMatch(path -> path.identityIds().equals(List.of(repository, service, controller))));
    }

    @Test
    void boundsCallerTraversal() {
        String a = method("com.acme.A", "a", "()V");
        String b = method("com.acme.B", "b", "()V");
        String c = method("com.acme.C", "c", "()V");
        CurrentFactReport report = CurrentFactReport.from("shop", List.of(
                call(a, c),
                call(b, c)));

        CallerTraversalResult result = CallerTraversalEngine.defaults().findCallers(report, c, 2, 1);

        assertEquals(1, result.callerPaths().size());
        assertTrue(result.truncated());
    }

    private static FactRecord call(String source, String target) {
        return FactRecord.create(
                List.of("src/main/java"),
                source,
                target,
                "CALLS",
                "direct",
                "shop",
                "snapshot-1",
                "analysis-1",
                "scope-1",
                "spoon",
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
