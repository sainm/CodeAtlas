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

class JpaEntityAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsJpaEntityTableAndColumnMappings() throws IOException {
        write("src/main/java/com/acme/UserEntity.java", """
                package com.acme;

                import jakarta.persistence.Column;
                import jakarta.persistence.Entity;
                import jakarta.persistence.Id;
                import jakarta.persistence.Table;
                import jakarta.persistence.Transient;

                @Entity
                @Table(name = "users", schema = "crm")
                class UserEntity {
                    @Id
                    @Column(name = "user_id")
                    Long id;

                    @Column(name = "display_name")
                    String name;

                    String email;

                    @Transient
                    String ignored;

                    transient String localCache;
                }
                """);

        JpaEntityAnalysisResult result = JpaEntityAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/java/com/acme/UserEntity.java")));

        assertTrue(result.diagnostics().isEmpty());
        assertEquals(1, result.entities().size());
        JpaEntityInfo entity = result.entities().getFirst();
        assertEquals("com.acme.UserEntity", entity.qualifiedName());
        assertEquals("users", entity.tableName());
        assertEquals("crm", entity.schemaName());
        assertEquals(3, entity.columns().size());
        assertTrue(entity.columns().stream()
                .anyMatch(column -> column.fieldName().equals("id")
                        && column.columnName().equals("user_id")
                        && column.fieldTypeDescriptor().equals("Ljava/lang/Long;")));
        assertTrue(entity.columns().stream()
                .anyMatch(column -> column.fieldName().equals("name")
                        && column.columnName().equals("display_name")));
        assertTrue(entity.columns().stream()
                .anyMatch(column -> column.fieldName().equals("email")
                        && column.columnName().equals("email")));
        assertTrue(entity.columns().stream()
                .noneMatch(column -> column.fieldName().equals("localCache")));
    }

    @Test
    void usesDefaultTableAndColumnNamesForAnnotatedJpaFields() throws IOException {
        write("src/main/java/com/acme/Account.java", """
                package com.acme;

                import javax.persistence.Entity;
                import javax.persistence.Id;

                @Entity
                class Account {
                    @Id
                    String accountNo;
                }
                """);

        JpaEntityAnalysisResult result = JpaEntityAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/java/com/acme/Account.java")));

        assertTrue(result.diagnostics().isEmpty());
        assertEquals("Account", result.entities().getFirst().tableName());
        assertTrue(result.entities().getFirst().columns().stream()
                .anyMatch(column -> column.fieldName().equals("accountNo")
                        && column.columnName().equals("accountNo")));
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
