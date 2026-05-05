package org.sainm.codeatlas.analyzers.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcSqlAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsJdbcSqlLiteralsAndSimpleConstantConcatenation() throws IOException {
        write("src/main/java/com/acme/UserRepository.java", """
                package com.acme;

                import java.sql.Connection;
                import java.sql.SQLException;

                class UserRepository {
                    void load(Connection connection) throws SQLException {
                        final String table = "users";
                        connection.prepareStatement("select * from " + table + " where id = ?");
                        connection.createStatement().executeUpdate("update accounts set name = 'x'");
                    }
                }
                """);

        JdbcSqlAnalysisResult result = JdbcSqlAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/java/com/acme/UserRepository.java")));

        assertTrue(result.diagnostics().isEmpty());
        assertEquals(2, result.statements().size());
        assertTrue(result.sqlStatementSources().stream()
                .anyMatch(source -> source.sql().equals("select * from users where id = ?")));
        assertTrue(result.sqlStatementSources().stream()
                .anyMatch(source -> source.sql().equals("update accounts set name = 'x'")));
    }

    @Test
    void ignoresJdbcLikeCallsOutsideMethods() throws IOException {
        write("src/main/java/com/acme/UserRepository.java", """
                package com.acme;

                class UserRepository {
                    Object statement = Driver.prepareStatement("select * from users");
                }

                class Driver {
                    static Object prepareStatement(String sql) {
                        return new Object();
                    }
                }
                """);

        JdbcSqlAnalysisResult result = JdbcSqlAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/java/com/acme/UserRepository.java")));

        assertTrue(result.diagnostics().isEmpty());
        assertTrue(result.statements().isEmpty());
    }

    @Test
    void ignoresReassignedSqlVariables() throws IOException {
        write("src/main/java/com/acme/UserRepository.java", """
                package com.acme;

                class UserRepository {
                    void load(Driver driver, String runtimeSql) {
                        String sql = "select * from users";
                        sql = runtimeSql;
                        driver.prepareStatement(sql);
                    }
                }

                class Driver {
                    Object prepareStatement(String sql) {
                        return new Object();
                    }
                }
                """);

        JdbcSqlAnalysisResult result = JdbcSqlAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/java/com/acme/UserRepository.java")));

        assertTrue(result.diagnostics().isEmpty());
        assertTrue(result.statements().isEmpty());
    }

    @Test
    void ignoresNonJdbcMethodsWithJdbcLikeNames() throws IOException {
        write("src/main/java/com/acme/UserRepository.java", """
                package com.acme;

                class UserRepository {
                    void load(QueryBuilder builder) {
                        builder.executeQuery("select * from users");
                    }
                }

                class QueryBuilder {
                    Object executeQuery(String queryName) {
                        return new Object();
                    }
                }
                """);

        JdbcSqlAnalysisResult result = JdbcSqlAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/java/com/acme/UserRepository.java")));

        assertTrue(result.diagnostics().isEmpty());
        assertTrue(result.statements().isEmpty());
    }

    @Test
    void extractsPreparedStatementParameterBindings() throws IOException {
        write("src/main/java/com/acme/UserRepository.java", """
                package com.acme;

                import java.sql.Connection;
                import java.sql.PreparedStatement;
                import java.sql.SQLException;

                class UserRepository {
                    void load(Connection connection, String name) throws SQLException {
                        PreparedStatement statement = connection.prepareStatement("select * from users where id = ? and name = ?");
                        statement.setLong(1, 42L);
                        statement.setString(2, name);
                    }
                }
                """);

        JdbcSqlAnalysisResult result = JdbcSqlAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/java/com/acme/UserRepository.java")));

        assertTrue(result.diagnostics().isEmpty());
        assertEquals(1, result.statements().size());
        assertEquals(2, result.statements().getFirst().parameters().size());
        assertTrue(result.statements().getFirst().parameters().stream()
                .anyMatch(parameter -> parameter.index() == 1
                        && parameter.binderMethodName().equals("setLong")));
        assertTrue(result.statements().getFirst().parameters().stream()
                .anyMatch(parameter -> parameter.index() == 2
                        && parameter.binderMethodName().equals("setString")));
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
