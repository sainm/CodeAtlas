package org.sainm.codeatlas.analyzers.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class SqlTableAnalyzerTest {
    @Test
    void extractsReadAndWriteTablesFromSqlStatements() {
        SourceLocation location = new SourceLocation("src/main/resources/com/acme/UserMapper.xml", 1, 1);

        SqlTableAnalysisResult result = SqlTableAnalyzer.defaults().analyze(List.of(
                new SqlStatementSourceInfo(
                        "com.acme.UserMapper.find",
                        "select u.id from users u join accounts a on a.user_id = u.id where u.id = ?",
                        location),
                new SqlStatementSourceInfo(
                        "com.acme.UserMapper.updateName",
                        "update users set name = ? where id = ?",
                        location),
                new SqlStatementSourceInfo(
                        "com.acme.UserMapper.deleteAccount",
                        "delete from accounts where id = ?",
                        location),
                new SqlStatementSourceInfo(
                        "com.acme.UserMapper.insertAudit",
                        "insert into audit_log(id, name) values (?, ?)",
                        location)));

        assertTrue(result.diagnostics().isEmpty());
        assertTable(result, "com.acme.UserMapper.find", "users", SqlTableAccessKind.READ);
        assertTable(result, "com.acme.UserMapper.find", "accounts", SqlTableAccessKind.READ);
        assertTable(result, "com.acme.UserMapper.updateName", "users", SqlTableAccessKind.WRITE);
        assertTable(result, "com.acme.UserMapper.deleteAccount", "accounts", SqlTableAccessKind.WRITE);
        assertTable(result, "com.acme.UserMapper.insertAudit", "audit_log", SqlTableAccessKind.WRITE);
        assertColumn(result, "com.acme.UserMapper.find", "users", "id", SqlTableAccessKind.READ);
        assertColumn(result, "com.acme.UserMapper.find", "accounts", "user_id", SqlTableAccessKind.READ);
        assertColumn(result, "com.acme.UserMapper.updateName", "users", "id", SqlTableAccessKind.READ);
        assertColumn(result, "com.acme.UserMapper.deleteAccount", "accounts", "id", SqlTableAccessKind.READ);
        assertColumn(result, "com.acme.UserMapper.updateName", "users", "name", SqlTableAccessKind.WRITE);
        assertColumn(result, "com.acme.UserMapper.insertAudit", "audit_log", "id", SqlTableAccessKind.WRITE);
        assertColumn(result, "com.acme.UserMapper.insertAudit", "audit_log", "name", SqlTableAccessKind.WRITE);
    }

    @Test
    void tracksUnqualifiedPredicateColumnsForSingleTableSql() {
        SourceLocation location = new SourceLocation("src/main/resources/com/acme/UserMapper.xml", 1, 1);

        SqlTableAnalysisResult result = SqlTableAnalyzer.defaults().analyze(List.of(
                new SqlStatementSourceInfo(
                        "selectByEmail",
                        "select id from users where email = ? and status in (?, ?)",
                        location),
                new SqlStatementSourceInfo(
                        "deleteByEmail",
                        "delete from users where email = ?",
                        location)));

        assertTrue(result.diagnostics().isEmpty());
        assertColumn(result, "selectByEmail", "users", "id", SqlTableAccessKind.READ);
        assertColumn(result, "selectByEmail", "users", "email", SqlTableAccessKind.READ);
        assertColumn(result, "selectByEmail", "users", "status", SqlTableAccessKind.READ);
        assertColumn(result, "deleteByEmail", "users", "email", SqlTableAccessKind.READ);
    }

    @Test
    void recordsDiagnosticsForUnparseableSql() {
        SourceLocation location = new SourceLocation("src/main/resources/com/acme/UserMapper.xml", 1, 1);

        SqlTableAnalysisResult result = SqlTableAnalyzer.defaults().analyze(List.of(
                new SqlStatementSourceInfo("broken", "select from", location)));

        assertTrue(result.tableAccesses().isEmpty());
        assertEquals(1, result.diagnostics().size());
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("SQL_PARSE_FAILED")));
    }

    @Test
    void separatesInsertTargetsFromSelectSources() {
        SourceLocation location = new SourceLocation("src/main/resources/com/acme/UserMapper.xml", 1, 1);

        SqlTableAnalysisResult result = SqlTableAnalyzer.defaults().analyze(List.of(
                new SqlStatementSourceInfo(
                        "com.acme.UserMapper.copyUsers",
                        "insert into audit_log(id) select id from users",
                        location)));

        assertTrue(result.diagnostics().isEmpty());
        assertTable(result, "com.acme.UserMapper.copyUsers", "audit_log", SqlTableAccessKind.WRITE);
        assertTable(result, "com.acme.UserMapper.copyUsers", "users", SqlTableAccessKind.READ);
        assertTrue(result.tableAccesses().stream().noneMatch(access -> access.statementId().equals("com.acme.UserMapper.copyUsers")
                && access.tableName().equals("users")
                && access.kind() == SqlTableAccessKind.WRITE));
    }

    @Test
    void recordsReadTablesInsideWriteStatements() {
        SourceLocation location = new SourceLocation("src/main/resources/com/acme/UserMapper.xml", 1, 1);

        SqlTableAnalysisResult result = SqlTableAnalyzer.defaults().analyze(List.of(
                new SqlStatementSourceInfo(
                        "com.acme.UserMapper.flagUsers",
                        "update users set flag = 1 where exists (select 1 from accounts where accounts.user_id = users.id)",
                        location),
                new SqlStatementSourceInfo(
                        "com.acme.UserMapper.deleteInactive",
                        "delete from users where id in (select user_id from archived_accounts)",
                        location)));

        assertTrue(result.diagnostics().isEmpty());
        assertTable(result, "com.acme.UserMapper.flagUsers", "users", SqlTableAccessKind.WRITE);
        assertTable(result, "com.acme.UserMapper.flagUsers", "accounts", SqlTableAccessKind.READ);
        assertTable(result, "com.acme.UserMapper.deleteInactive", "users", SqlTableAccessKind.WRITE);
        assertTable(result, "com.acme.UserMapper.deleteInactive", "archived_accounts", SqlTableAccessKind.READ);
    }


    @Test
    void fallsBackConservativelyForMyBatisDynamicSql() {
        SourceLocation location = new SourceLocation("src/main/resources/com/acme/UserMapper.xml", 1, 1);

        SqlTableAnalysisResult result = SqlTableAnalyzer.defaults().analyze(List.of(
                new SqlStatementSourceInfo(
                        "com.acme.UserMapper.search",
                        """
                                <script>
                                  select u.id
                                  from users u
                                  <where>
                                    <if test="name != null">
                                      and u.name = #{name}
                                    </if>
                                  </where>
                                </script>
                                """,
                        location)));

        assertTrue(result.diagnostics().isEmpty());
        assertTable(result, "com.acme.UserMapper.search", "users", SqlTableAccessKind.READ, true);
    }

    @Test
    void preservesSqlComparisonsThatLookLikeXmlTags() {
        SourceLocation location = new SourceLocation("src/main/java/com/acme/UserRepository.java", 12, 9);

        SqlTableAnalysisResult result = SqlTableAnalyzer.defaults().analyze(List.of(
                new SqlStatementSourceInfo(
                        "com.acme.UserRepository.load(Ljava/sql/Connection;)V@12",
                        "select * from users where id < (select max(id) from accounts) and id > 0",
                        location)));

        assertTrue(result.diagnostics().isEmpty());
        assertTable(result, "com.acme.UserRepository.load(Ljava/sql/Connection;)V@12", "users", SqlTableAccessKind.READ);
        assertTable(result, "com.acme.UserRepository.load(Ljava/sql/Connection;)V@12", "accounts", SqlTableAccessKind.READ);
    }

    @Test
    void extractsTablesFromAllJoinVariants() {
        SourceLocation location = new SourceLocation("src/main/resources/com/acme/Mapper.xml", 1, 1);

        SqlTableAnalysisResult result = SqlTableAnalyzer.defaults().analyze(List.of(
                new SqlStatementSourceInfo("s1", "select * from a left join b on a.id = b.a_id", location),
                new SqlStatementSourceInfo("s2", "select * from a right join b on a.id = b.a_id", location),
                new SqlStatementSourceInfo("s3", "select * from a inner join b on a.id = b.a_id", location),
                new SqlStatementSourceInfo("s4", "select * from a cross join b", location),
                new SqlStatementSourceInfo("s5", "select * from a full join b on a.id = b.a_id", location),
                new SqlStatementSourceInfo("s6", "select * from a natural join b", location),
                new SqlStatementSourceInfo("s7", "select * from a left outer join b on a.id = b.a_id", location),
                new SqlStatementSourceInfo("s8", "select * from a right outer join b on a.id = b.a_id", location),
                new SqlStatementSourceInfo("s9", "select * from a full outer join b on a.id = b.a_id", location)));

        assertTrue(result.diagnostics().isEmpty());
        for (int i = 1; i <= 9; i++) {
            assertTable(result, "s" + i, "a", SqlTableAccessKind.READ);
            assertTable(result, "s" + i, "b", SqlTableAccessKind.READ);
        }
    }

    @Test
    void resolvesTableAliasesWithAllJoinVariants() {
        SourceLocation location = new SourceLocation("src/main/resources/com/acme/Mapper.xml", 1, 1);

        SqlTableAnalysisResult result = SqlTableAnalyzer.defaults().analyze(List.of(
                new SqlStatementSourceInfo("s1",
                        "select l.id, r.name from t l left join u r on l.id = r.t_id", location)));

        assertTrue(result.diagnostics().isEmpty());
        assertTable(result, "s1", "t", SqlTableAccessKind.READ);
        assertTable(result, "s1", "u", SqlTableAccessKind.READ);
        assertColumn(result, "s1", "t", "id", SqlTableAccessKind.READ);
    }

    private static void assertTable(
            SqlTableAnalysisResult result,
            String statementId,
            String tableName,
            SqlTableAccessKind kind) {
        assertTrue(result.tableAccesses().stream().anyMatch(access -> access.statementId().equals(statementId)
                && access.tableName().equals(tableName)
                && access.kind() == kind), statementId + " " + kind + " " + tableName);
    }

    private static void assertTable(
            SqlTableAnalysisResult result,
            String statementId,
            String tableName,
            SqlTableAccessKind kind,
            boolean conservativeFallback) {
        assertTrue(result.tableAccesses().stream().anyMatch(access -> access.statementId().equals(statementId)
                && access.tableName().equals(tableName)
                && access.kind() == kind
                && access.conservativeFallback() == conservativeFallback), statementId + " " + kind + " " + tableName);
    }

    private static void assertColumn(
            SqlTableAnalysisResult result,
            String statementId,
            String tableName,
            String columnName,
            SqlTableAccessKind kind) {
        assertTrue(result.columnAccesses().stream().anyMatch(access -> access.statementId().equals(statementId)
                && access.tableName().equals(tableName)
                && access.columnName().equals(columnName)
                && access.kind() == kind), statementId + " " + kind + " " + tableName + "." + columnName);
    }
}
