package org.sainm.codeatlas.analyzers.source;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sainm.codeatlas.facts.Confidence;

class SqlTableFactMapperTest {
    @Test
    void mapsSqlTableAccessesToReadAndWriteFacts() {
        SourceLocation location = new SourceLocation("src/main/resources/com/acme/UserMapper.xml", 12, 5);
        SqlTableAnalysisResult tables = new SqlTableAnalysisResult(
                List.of(
                        new SqlTableAccessInfo("com.acme.UserMapper.find", "users", SqlTableAccessKind.READ, location),
                        new SqlTableAccessInfo("com.acme.UserMapper.update", "users", SqlTableAccessKind.WRITE, true, location)),
                List.of(
                        new SqlColumnAccessInfo("com.acme.UserMapper.find", "users", "id", SqlTableAccessKind.READ, location),
                        new SqlColumnAccessInfo("com.acme.UserMapper.update", "users", "name", SqlTableAccessKind.WRITE, location)),
                List.of());

        JavaSourceFactBatch batch = SqlTableFactMapper.defaults().map(
                tables,
                new SqlTableFactContext(
                        "shop",
                        "_root",
                        "src/main/resources",
                        "mainDs",
                        "public",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/resources/com/acme/UserMapper.xml"));

        assertFact(batch,
                "READS_TABLE",
                "sql-statement://shop/_root/src/main/resources/com/acme/UserMapper.xml#com.acme.UserMapper.find",
                "db-table://shop/mainDs/public/users",
                Confidence.CERTAIN);
        assertFact(batch,
                "WRITES_TABLE",
                "sql-statement://shop/_root/src/main/resources/com/acme/UserMapper.xml#com.acme.UserMapper.update",
                "db-table://shop/mainDs/public/users",
                Confidence.POSSIBLE);
        assertFact(batch,
                "READS_COLUMN",
                "sql-statement://shop/_root/src/main/resources/com/acme/UserMapper.xml#com.acme.UserMapper.find",
                "db-column://shop/mainDs/public/users#id",
                Confidence.CERTAIN);
        assertFact(batch,
                "WRITES_COLUMN",
                "sql-statement://shop/_root/src/main/resources/com/acme/UserMapper.xml#com.acme.UserMapper.update",
                "db-column://shop/mainDs/public/users#name",
                Confidence.CERTAIN);
    }

    @Test
    void doesNotDoublePrefixSchemaQualifiedTableAndColumnNames() {
        SourceLocation location = new SourceLocation("src/main/resources/com/acme/UserMapper.xml", 12, 5);
        SqlTableAnalysisResult tables = new SqlTableAnalysisResult(
                List.of(new SqlTableAccessInfo("com.acme.UserMapper.find", "public.users", SqlTableAccessKind.READ, location)),
                List.of(new SqlColumnAccessInfo("com.acme.UserMapper.find", "public.users", "id", SqlTableAccessKind.READ, location)),
                List.of());

        JavaSourceFactBatch batch = SqlTableFactMapper.defaults().map(
                tables,
                new SqlTableFactContext(
                        "shop",
                        "_root",
                        "src/main/resources",
                        "mainDs",
                        "public",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/resources/com/acme/UserMapper.xml"));

        assertFact(batch,
                "READS_TABLE",
                "sql-statement://shop/_root/src/main/resources/com/acme/UserMapper.xml#com.acme.UserMapper.find",
                "db-table://shop/mainDs/public/users",
                Confidence.CERTAIN);
        assertFact(batch,
                "READS_COLUMN",
                "sql-statement://shop/_root/src/main/resources/com/acme/UserMapper.xml#com.acme.UserMapper.find",
                "db-column://shop/mainDs/public/users#id",
                Confidence.CERTAIN);
    }

    private static void assertFact(
            JavaSourceFactBatch batch,
            String relation,
            String source,
            String target,
            Confidence confidence) {
        assertTrue(batch.facts().stream().anyMatch(fact -> fact.relationType().name().equals(relation)
                && fact.sourceIdentityId().equals(source)
                && fact.targetIdentityId().equals(target)
                && fact.confidence() == confidence), relation + " " + source + " -> " + target);
    }
}
