package org.sainm.codeatlas.analyzers.sql;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SymbolKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JpaEntityAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void linksJpaEntityAndFieldsToDatabaseTableAndColumns() throws Exception {
        Path source = tempDir.resolve("src/main/java/com/acme/UserEntity.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package com.acme;

            import jakarta.persistence.Column;
            import jakarta.persistence.Entity;
            import jakarta.persistence.Id;
            import jakarta.persistence.Table;
            import jakarta.persistence.Transient;

            @Entity
            @Table(name = "users")
            class UserEntity {
                @Id
                @Column(name = "user_id")
                private String id;

                private String displayName;

                @Transient
                private String temporaryToken;

                static String TYPE = "USER";
            }
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "src/main/java", tempDir);
        JpaEntityAnalysisResult result = new JpaEntityAnalyzer().analyze(scope, "shop", "src/main/java", List.of(source));

        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.DB_TABLE
            && node.symbolId().ownerQualifiedName().equals("users")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.BINDS_TO
            && fact.factKey().source().kind() == SymbolKind.CLASS
            && fact.factKey().source().ownerQualifiedName().equals("com.acme.UserEntity")
            && fact.factKey().target().kind() == SymbolKind.DB_TABLE
            && fact.factKey().target().ownerQualifiedName().equals("users")
            && fact.evidenceKey().path().endsWith("UserEntity.java")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.BINDS_TO
            && fact.factKey().source().kind() == SymbolKind.FIELD
            && fact.factKey().source().memberName().equals("id")
            && fact.factKey().target().kind() == SymbolKind.DB_COLUMN
            && fact.factKey().target().localId().equals("user_id")
            && fact.confidence() == Confidence.LIKELY));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.BINDS_TO
            && fact.factKey().source().kind() == SymbolKind.FIELD
            && fact.factKey().source().memberName().equals("displayName")
            && fact.factKey().target().kind() == SymbolKind.DB_COLUMN
            && fact.factKey().target().localId().equals("displayName")
            && fact.confidence() == Confidence.POSSIBLE));
        assertTrue(result.facts().stream().noneMatch(fact -> fact.factKey().source().kind() == SymbolKind.FIELD
            && fact.factKey().source().memberName().equals("temporaryToken")));
        assertTrue(result.facts().stream().noneMatch(fact -> fact.factKey().source().kind() == SymbolKind.FIELD
            && fact.factKey().source().memberName().equals("TYPE")));
    }
}
