package org.sainm.codeatlas.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sainm.codeatlas.facts.Confidence;
import org.sainm.codeatlas.facts.CurrentFactReport;
import org.sainm.codeatlas.facts.FactRecord;
import org.sainm.codeatlas.facts.SourceType;

class DownstreamTraversalEngineTest {
    @Test
    void traversesEntrypointToSqlAndDatabaseTargets() {
        String entrypoint = "entrypoint://shop/_root/_entrypoints/spring/GET/users";
        String controller = method("com.acme.UserController", "show", "()V");
        String service = method("com.acme.UserService", "load", "()V");
        String sql = "sql-statement://shop/_root/src/main/resources/com/acme/UserMapper.xml#com.acme.UserMapper.find";
        String table = "db-table://shop/mainDs/public/users";
        CurrentFactReport report = CurrentFactReport.from("shop", List.of(
                fact(entrypoint, controller, "ROUTES_TO"),
                fact(controller, service, "CALLS"),
                fact(service, sql, "BINDS_TO"),
                fact(sql, table, "READS_TABLE")));

        DownstreamTraversalResult result = DownstreamTraversalEngine.defaults().findDownstream(report, entrypoint, 5, 20);

        assertFalse(result.truncated());
        assertTrue(result.downstreamPaths().stream()
                .anyMatch(path -> path.identityIds().equals(List.of(entrypoint, controller, service, sql, table))));
    }

    private static FactRecord fact(String source, String target, String relation) {
        return FactRecord.create(
                List.of("src/main/java", "src/main/resources", "_entrypoints"),
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
