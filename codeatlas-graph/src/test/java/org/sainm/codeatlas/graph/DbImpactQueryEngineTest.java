package org.sainm.codeatlas.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sainm.codeatlas.facts.Confidence;
import org.sainm.codeatlas.facts.CurrentFactReport;
import org.sainm.codeatlas.facts.FactRecord;
import org.sainm.codeatlas.facts.SourceType;

class DbImpactQueryEngineTest {
    @Test
    void findsTableImpactFactsAndUpstreamBinders() {
        String sql = "sql-statement://shop/_root/src/main/resources/com/acme/UserMapper.xml#com.acme.UserMapper.find";
        String method = "method://shop/_root/src/main/java/com.acme.UserMapper#find()Ljava/util/List;";
        String users = "db-table://shop/mainDs/public/users";
        String userId = "db-column://shop/mainDs/public/users#id";
        CurrentFactReport report = CurrentFactReport.from("shop", List.of(
                fact(sql, users, "READS_TABLE", "sql-table"),
                fact(sql, userId, "READS_COLUMN", "sql-column"),
                fact(method, sql, "BINDS_TO", "mybatis-mapper-method"),
                fact(sql, "db-table://shop/mainDs/public/accounts", "READS_TABLE", "sql-table")));

        DbImpactQueryResult result = DbImpactQueryEngine.defaults().tableImpact(report, users);

        assertEquals(users, result.targetIdentityId());
        assertEquals(2, result.databaseFacts().size());
        assertEquals(2, result.readFacts().size());
        assertTrue(result.upstreamBindingFacts().stream()
                .anyMatch(fact -> fact.sourceIdentityId().equals(method)
                        && fact.targetIdentityId().equals(sql)));
    }

    @Test
    void findsColumnImpactWithoutUnrelatedTableFacts() {
        String sql = "sql-statement://shop/_root/src/main/resources/com/acme/UserMapper.xml#com.acme.UserMapper.update";
        String userName = "db-column://shop/mainDs/public/users#name";
        String users = "db-table://shop/mainDs/public/users";
        CurrentFactReport report = CurrentFactReport.from("shop", List.of(
                fact(sql, users, "WRITES_TABLE", "sql-table"),
                fact(sql, userName, "WRITES_COLUMN", "sql-column"),
                fact("field://shop/_root/src/main/java/com.acme.UserEntity#name:Ljava/lang/String;",
                        userName,
                        "MAPS_TO_COLUMN",
                        "jpa-field-column")));

        DbImpactQueryResult result = DbImpactQueryEngine.defaults().columnImpact(report, userName);

        assertEquals(userName, result.targetIdentityId());
        assertEquals(2, result.databaseFacts().size());
        assertEquals(1, result.writeFacts().size());
        assertTrue(result.databaseFacts().stream().noneMatch(fact -> fact.targetIdentityId().equals(users)));
    }

    private static FactRecord fact(String source, String target, String relation, String qualifier) {
        return FactRecord.create(
                List.of("src/main/java", "src/main/resources"),
                source,
                target,
                relation,
                qualifier,
                "shop",
                "snapshot-1",
                "analysis-1",
                "scope-1",
                "query-test",
                "src/main/resources/com/acme/UserMapper.xml",
                "evidence-1",
                Confidence.CERTAIN,
                100,
                SourceType.SQL);
    }
}
