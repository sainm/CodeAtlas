package org.sainm.codeatlas.analyzers.workspace;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChangedFileSymbolResolverTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesChangedFilesToCodeSqlConfigAndDatabaseSymbols() throws IOException {
        write("src/main/java/com/acme/UserRepository.java", """
                package com.acme;

                import java.sql.Connection;
                import java.sql.SQLException;

                class UserRepository {
                    void load(Connection connection) throws SQLException {
                        connection.prepareStatement("select id from users where id = ?");
                    }
                }
                """);
        write("src/main/resources/com/acme/UserMapper.xml", """
                <mapper namespace="com.acme.UserMapper">
                  <select id="find">select id from users where id = #{id}</select>
                </mapper>
                """);
        write("src/main/webapp/WEB-INF/jsp/user.jsp", "<html></html>\n");
        write("src/main/resources/application.yml", "feature.enabled: true\n");

        ChangedSymbolResolution result = ChangedFileSymbolResolver.defaults().resolve(
                tempDir,
                List.of(
                        changed("src/main/java/com/acme/UserRepository.java", 7),
                        changed("src/main/resources/com/acme/UserMapper.xml", 2),
                        changed("src/main/webapp/WEB-INF/jsp/user.jsp", 1),
                        changed("src/main/resources/application.yml", 1)),
                new ChangedSymbolContext(
                        "shop",
                        "_root",
                        "src/main/java",
                        "src/main/resources",
                        "src/main/webapp",
                        "mainDs",
                        "public"));

        assertTrue(result.diagnostics().isEmpty());
        assertSymbol(result, "class", "class://shop/_root/src/main/java/com.acme.UserRepository");
        assertSymbol(result, "method", "method://shop/_root/src/main/java/com.acme.UserRepository#load(Ljava/sql/Connection;)V");
        assertSymbol(result, "sql-statement",
                "sql-statement://shop/_root/src/main/resources/com/acme/UserMapper.xml#com.acme.UserMapper.find");
        assertSymbol(result, "db-table", "db-table://shop/mainDs/public/users");
        assertSymbol(result, "jsp-page", "jsp-page://shop/_root/src/main/webapp/WEB-INF/jsp/user.jsp");
        assertSymbol(result, "config-key", "config-key://shop/_root/src/main/resources/application.yml#application.yml");
    }

    private static GitChangedFile changed(String path, int line) {
        return new GitChangedFile("", path, "MODIFY", List.of(new GitChangedHunk(line, 1, line, 1)));
    }

    private static void assertSymbol(ChangedSymbolResolution result, String kind, String identityId) {
        assertTrue(result.symbols().stream().anyMatch(symbol -> symbol.kind().equals(kind)
                && symbol.identityId().equals(identityId)), kind + " " + identityId);
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
