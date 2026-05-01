package org.sainm.codeatlas.analyzers.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.NodeRole;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SymbolKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MyBatisMapperInterfaceAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsMapperInterfaceMethodsAndStatementAliasBridge() throws Exception {
        Path source = tempDir.resolve("src/main/java/com/acme/UserMapper.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package com.acme;

            import org.apache.ibatis.annotations.Mapper;

            @Mapper
            interface UserMapper {
                User findById(String id);
            }

            class User {
            }
            """);

        MyBatisMapperInterfaceAnalysisResult result = analyze(source);

        assertEquals(1, result.methods().size());
        assertEquals("com.acme.UserMapper", result.methods().getFirst().mapperInterface());
        assertEquals("findById", result.methods().getFirst().methodName());
        assertEquals(Confidence.CERTAIN, result.methods().getFirst().bridgeConfidence());
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.INTERFACE
            && node.roles().contains(NodeRole.MAPPER)));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.BRIDGES_TO
            && fact.factKey().source().descriptor().contains("java.lang.String")
            && fact.factKey().target().descriptor().equals("_unknown")));
    }

    @Test
    void extractsAnnotatedSqlAndTableFacts() throws Exception {
        Path source = tempDir.resolve("src/main/java/com/acme/UserMapper.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package com.acme;

            import org.apache.ibatis.annotations.Select;

            interface UserMapper {
                @Select("select id, name from users where id = #{id}")
                User findById(String id);
            }

            class User {
            }
            """);

        MyBatisMapperInterfaceAnalysisResult result = analyze(source);

        assertEquals(1, result.methods().size());
        assertTrue(result.methods().getFirst().annotationSql());
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.BINDS_TO
            && fact.factKey().target().kind() == SymbolKind.SQL_STATEMENT));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.READS_TABLE
            && fact.factKey().target().ownerQualifiedName().endsWith("users")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().target().kind() == SymbolKind.DB_COLUMN
            && fact.factKey().target().localId().equals("id")));
    }

    @Test
    void extractsAnnotatedSqlArrayWithoutSourceTextParsing() throws Exception {
        Path source = tempDir.resolve("src/main/java/com/acme/UserMapper.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package com.acme;

            import org.apache.ibatis.annotations.Update;

            interface UserMapper {
                @Update({
                    "update users",
                    "set name = #{name}",
                    "where id = #{id}"
                })
                int rename(String id, String name);
            }
            """);

        MyBatisMapperInterfaceAnalysisResult result = analyze(source);

        assertEquals(1, result.methods().size());
        assertTrue(result.methods().getFirst().annotationSql());
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.WRITES_TABLE
            && fact.factKey().target().kind() == SymbolKind.DB_TABLE
            && fact.factKey().target().ownerQualifiedName().equals("users")));
    }

    private MyBatisMapperInterfaceAnalysisResult analyze(Path source) {
        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "src/main/java", tempDir);
        return new MyBatisMapperInterfaceAnalyzer().analyze(scope, "shop", "src/main/java", List.of(source));
    }
}
