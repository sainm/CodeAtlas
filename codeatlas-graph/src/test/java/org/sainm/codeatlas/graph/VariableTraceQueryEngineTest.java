package org.sainm.codeatlas.graph;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sainm.codeatlas.facts.Confidence;
import org.sainm.codeatlas.facts.CurrentFactReport;
import org.sainm.codeatlas.facts.FactRecord;
import org.sainm.codeatlas.facts.SourceType;

class VariableTraceQueryEngineTest {
    @Test
    void tracesJspInputToRequestParameterAndJavaParameterSourcePath() {
        String jspInput = "jsp-input://shop/_root/src/main/webapp/user.jsp#form[user]:input[id]";
        String request = "request-param://shop/_root/src/main/webapp#id";
        String param = param("com.acme.UserController", "handle", "(Ljava/lang/String;)V", 0);
        CurrentFactReport report = CurrentFactReport.from("shop", List.of(
                fact(jspInput, request, "BINDS_TO"),
                fact(request, param, "PASSES_PARAM")));

        VariableTraceReport result = VariableTraceQueryEngine.defaults().traceSources(report, param, 4, 10);

        assertTrue(result.sourcePaths().stream()
                .anyMatch(path -> path.identityIds().equals(List.of(param, request, jspInput))));
    }

    @Test
    void tracesJavaParameterToSqlParameterAndTableSinks() {
        String param = param("com.acme.UserDao", "find", "(Ljava/lang/String;)V", 0);
        String dao = "method://shop/_root/src/main/java/com.acme.UserDao#find(Ljava/lang/String;)V";
        String sql = "sql-statement://shop/_root/src/main/resources/com/acme/UserMapper.xml#find";
        String sqlParam = "sql-param://shop/_root/src/main/resources/com/acme/UserMapper.xml#find:param[1]";
        String table = "db-table://shop/mainDs/public/users";
        CurrentFactReport report = CurrentFactReport.from("shop", List.of(
                fact(dao, sql, "BINDS_TO"),
                fact(sql, sqlParam, "HAS_PARAM"),
                fact(sql, table, "READS_TABLE")));

        VariableTraceReport result = VariableTraceQueryEngine.defaults().traceSinks(report, param, 4, 10);

        assertTrue(result.sinkPaths().stream()
                .anyMatch(path -> path.identityIds().equals(List.of(param, sql, sqlParam))));
        assertTrue(result.sinkPaths().stream()
                .anyMatch(path -> path.identityIds().equals(List.of(param, sql, table))));
    }

    @Test
    void buildsCombinedReportAcrossJspSourceAndSqlSink() {
        String jspInput = "jsp-input://shop/_root/src/main/webapp/user.jsp#form[user]:input[id]";
        String request = "request-param://shop/_root/src/main/webapp#id";
        String param = param("com.acme.UserDao", "find", "(Ljava/lang/String;)V", 0);
        String dao = "method://shop/_root/src/main/java/com.acme.UserDao#find(Ljava/lang/String;)V";
        String sql = "sql-statement://shop/_root/src/main/resources/com/acme/UserMapper.xml#find";
        String sqlParam = "sql-param://shop/_root/src/main/resources/com/acme/UserMapper.xml#find:param[1]";
        CurrentFactReport report = CurrentFactReport.from("shop", List.of(
                fact(jspInput, request, "BINDS_TO"),
                fact(request, param, "PASSES_PARAM"),
                fact(dao, sql, "BINDS_TO"),
                fact(sql, sqlParam, "HAS_PARAM")));

        VariableTraceReport result = VariableTraceQueryEngine.defaults().traceCombined(report, param, 6, 20);

        assertTrue(result.sourcePaths().stream()
                .anyMatch(path -> path.identityIds().equals(List.of(param, request, jspInput))));
        assertTrue(result.sinkPaths().stream()
                .anyMatch(path -> path.identityIds().equals(List.of(param, sql, sqlParam))));
        assertTrue(result.combinedPaths().size() >= 2);
    }


    private static FactRecord fact(String source, String target, String relation) {
        return FactRecord.create(
                List.of("src/main/java", "src/main/resources", "src/main/webapp"),
                source,
                target,
                relation,
                "test",
                "shop",
                "snapshot-1",
                "analysis-1",
                "scope-1",
                "variable-trace",
                "src/main/java",
                "evidence-1",
                Confidence.LIKELY,
                90,
                SourceType.IMPACT_FLOW);
    }

    private static String param(String owner, String method, String signature, int index) {
        return "param-slot://shop/_root/src/main/java/" + owner + "#" + method + signature
                + ":param[" + index + ":Ljava/lang/String;]";
    }
}
