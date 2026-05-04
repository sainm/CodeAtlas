package org.sainm.codeatlas.analyzers.source;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class JdbcSqlFactMapperTest {
    @Test
    void mapsJdbcMethodsToSqlStatements() {
        SourceLocation location = new SourceLocation("src/main/java/com/acme/UserRepository.java", 9, 13);
        JdbcSqlAnalysisResult jdbc = new JdbcSqlAnalysisResult(
                List.of(new JdbcSqlStatementInfo(
                        "com.acme.UserRepository.load(Ljava/sql/Connection;)V@9",
                        "com.acme.UserRepository",
                        "load",
                        "(Ljava/sql/Connection;)V",
                        "select * from users",
                        location)),
                List.of());

        JavaSourceFactBatch batch = JdbcSqlFactMapper.defaults().map(
                jdbc,
                new JdbcSqlFactContext(
                        "shop",
                        "_root",
                        "src/main/java",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/java/com/acme/UserRepository.java"));

        assertTrue(batch.facts().stream().anyMatch(fact -> fact.relationType().name().equals("BINDS_TO")
                && fact.sourceIdentityId().equals("method://shop/_root/src/main/java/com.acme.UserRepository#load(Ljava/sql/Connection;)V")
                && fact.targetIdentityId().equals("sql-statement://shop/_root/src/main/java/com/acme/UserRepository.java#com.acme.UserRepository.load(Ljava/sql/Connection;)V@9")));
    }
}
