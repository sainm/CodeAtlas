package org.sainm.codeatlas.graph;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sainm.codeatlas.facts.Confidence;
import org.sainm.codeatlas.facts.CurrentFactReport;
import org.sainm.codeatlas.facts.FactRecord;
import org.sainm.codeatlas.facts.SourceType;

class WebBackendFlowSearchEngineTest {
    @Test
    void findsApiToServiceMapperSqlAndTableFlow() {
        String api = "api-endpoint://shop/_root/_api/GET:/users/{id}";
        String controller = method("com.acme.UserController", "show", "()V");
        String service = method("com.acme.UserService", "load", "()V");
        String mapper = method("com.acme.UserMapper", "find", "()V");
        String sql = "sql-statement://shop/_root/src/main/resources/com/acme/UserMapper.xml#com.acme.UserMapper.find";
        String table = "db-table://shop/mainDs/public/users";
        CurrentFactReport report = CurrentFactReport.from("shop", List.of(
                fact(api, controller, "ROUTES_TO"),
                fact(controller, service, "CALLS"),
                fact(service, mapper, "CALLS"),
                fact(mapper, sql, "BINDS_TO"),
                fact(sql, table, "READS_TABLE")));

        WebBackendFlowSearchResult result = WebBackendFlowSearchEngine.defaults()
                .findBackendFlows(report, api, 6, 20);

        assertTrue(result.backendFlowPaths().stream()
                .anyMatch(path -> path.identityIds().equals(List.of(api, controller, service, mapper, sql))));
        assertTrue(result.backendFlowPaths().stream()
                .anyMatch(path -> path.identityIds().equals(List.of(api, controller, service, mapper, sql, table))));
    }

    private static FactRecord fact(String source, String target, String relation) {
        return FactRecord.create(
                List.of("_api", "src/main/java", "src/main/resources"),
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
