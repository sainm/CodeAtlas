package org.sainm.codeatlas.analyzers.sql;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SymbolKind;

class JdbcSqlAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsJdbcSqlTableFactsFromConstantsAndDirectLiterals() throws Exception {
        Path javaFile = write("src/main/java/com/acme/dao/UserDao.java", """
            package com.acme.dao;
            import java.sql.Connection;
            import java.sql.Statement;
            class UserDao {
              void save(Connection connection, String id, String name) throws Exception {
                String updateSql = "update users set name = ? where id = ?";
                connection.prepareStatement(updateSql);
              }
              void audit(Statement statement) throws Exception {
                statement.executeUpdate("insert into audit_log (user_id, action) values (?, ?)");
              }
            }
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "src/main/java", tempDir);
        JdbcSqlAnalysisResult result = new JdbcSqlAnalyzer().analyze(scope, "shop", "src/main/java", java.util.List.of(javaFile));

        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.SQL_STATEMENT));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.BINDS_TO
            && fact.factKey().source().ownerQualifiedName().equals("com.acme.dao.UserDao")
            && fact.factKey().source().memberName().equals("save")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.WRITES_TABLE
            && fact.factKey().target().kind() == SymbolKind.DB_TABLE
            && fact.factKey().target().ownerQualifiedName().equals("users")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.WRITES_TABLE
            && fact.factKey().target().kind() == SymbolKind.DB_TABLE
            && fact.factKey().target().ownerQualifiedName().equals("audit_log")));
    }

    @Test
    void marksPartiallyDynamicJdbcSqlAsPossible() throws Exception {
        Path javaFile = write("src/main/java/com/acme/dao/SearchDao.java", """
            package com.acme.dao;
            import java.sql.Connection;
            class SearchDao {
              void search(Connection connection, String suffix) throws Exception {
                connection.prepareStatement("select id, name from users where name like " + suffix);
              }
            }
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "src/main/java", tempDir);
        JdbcSqlAnalysisResult result = new JdbcSqlAnalyzer().analyze(scope, "shop", "src/main/java", java.util.List.of(javaFile));

        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.READS_TABLE
            && fact.factKey().target().kind() == SymbolKind.DB_TABLE
            && fact.factKey().target().ownerQualifiedName().equals("users")
            && fact.confidence() == Confidence.POSSIBLE));
    }

    @Test
    void linksPreparedStatementParameterVariablesToJdbcSqlStatement() throws Exception {
        Path javaFile = write("src/main/java/com/acme/dao/UserDao.java", """
            package com.acme.dao;
            import java.sql.Connection;
            import java.sql.PreparedStatement;
            class UserDao {
              void updateName(Connection connection, String id, String name) throws Exception {
                String sql = "update users set name = ? where id = ?";
                PreparedStatement ps = connection.prepareStatement(sql);
                ps.setString(1, name);
                ps.setString(2, id);
                ps.executeUpdate();
              }
            }
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "src/main/java", tempDir);
        JdbcSqlAnalysisResult result = new JdbcSqlAnalyzer().analyze(scope, "shop", "src/main/java", java.util.List.of(javaFile));

        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.PASSES_PARAM
            && fact.factKey().source().ownerQualifiedName().equals("com.acme.dao.UserDao")
            && fact.factKey().source().memberName().equals("updateName")
            && fact.factKey().target().kind() == SymbolKind.SQL_STATEMENT
            && fact.factKey().qualifier().equals("jdbc-parameter:1:name")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.PASSES_PARAM
            && fact.factKey().target().kind() == SymbolKind.SQL_STATEMENT
            && fact.factKey().qualifier().equals("jdbc-parameter:2:id")));
    }

    private Path write(String relativePath, String content) throws Exception {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }
}
