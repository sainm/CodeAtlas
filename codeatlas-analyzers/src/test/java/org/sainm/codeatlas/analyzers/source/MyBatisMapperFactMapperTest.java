package org.sainm.codeatlas.analyzers.source;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MyBatisMapperFactMapperTest {
    @TempDir
    Path tempDir;

    @Test
    void mapsMapperMethodsToSqlStatementFacts() throws IOException {
        write("src/main/java/org/apache/ibatis/annotations/Mapper.java", """
                package org.apache.ibatis.annotations;

                public @interface Mapper {}
                """);
        write("src/main/java/com/acme/UserMapper.java", """
                package com.acme;

                import org.apache.ibatis.annotations.Mapper;

                @Mapper
                interface UserMapper {
                    User findById(String id);
                    int updateName(String id, String name);
                    void notMapped();
                }

                class User {}
                """);
        write("src/main/resources/com/acme/UserMapper.xml", """
                <mapper namespace="com.acme.UserMapper">
                  <select id="findById">select * from users where id = #{id}</select>
                  <update id="updateName">update users set name = #{name} where id = #{id}</update>
                </mapper>
                """);

        JavaSourceAnalysisResult source = JavaSourceAnalyzer.defaults().analyze(
                tempDir,
                List.of(
                        tempDir.resolve("src/main/java/org/apache/ibatis/annotations/Mapper.java"),
                        tempDir.resolve("src/main/java/com/acme/UserMapper.java")));
        MyBatisMapperAnalysisResult mappers = MyBatisMapperAnalyzer.defaults().analyze(source);
        MyBatisXmlAnalysisResult xml = MyBatisXmlAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/resources/com/acme/UserMapper.xml")));

        JavaSourceFactBatch batch = MyBatisMapperFactMapper.defaults().map(
                mappers,
                xml,
                new MyBatisFactContext(
                        "shop",
                        "_root",
                        "src/main/java",
                        "src/main/resources/",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/resources/com/acme/UserMapper.xml"));

        assertFalse(batch.evidence().isEmpty());
        assertFact(batch,
                "BINDS_TO",
                "method://shop/_root/src/main/java/com.acme.UserMapper#findById(Ljava/lang/String;)Lcom/acme/User;",
                "sql-statement://shop/_root/src/main/resources/com/acme/UserMapper.xml#com.acme.UserMapper.findById");
        assertFact(batch,
                "BINDS_TO",
                "method://shop/_root/src/main/java/com.acme.UserMapper#updateName(Ljava/lang/String;Ljava/lang/String;)I",
                "sql-statement://shop/_root/src/main/resources/com/acme/UserMapper.xml#com.acme.UserMapper.updateName");
        assertTrue(batch.facts().stream().noneMatch(fact -> fact.sourceIdentityId().contains("#notMapped(")));
    }

    @Test
    void doesNotBindOverloadedMapperMethodsToAmbiguousStatementId() throws IOException {
        write("src/main/java/org/apache/ibatis/annotations/Mapper.java", """
                package org.apache.ibatis.annotations;

                public @interface Mapper {}
                """);
        write("src/main/java/com/acme/UserMapper.java", """
                package com.acme;

                import org.apache.ibatis.annotations.Mapper;

                @Mapper
                interface UserMapper {
                    User find(String id);
                    User find(int id);
                }

                class User {}
                """);
        write("src/main/resources/com/acme/UserMapper.xml", """
                <mapper namespace="com.acme.UserMapper">
                  <select id="find">select * from users where id = #{id}</select>
                </mapper>
                """);

        JavaSourceAnalysisResult source = JavaSourceAnalyzer.defaults().analyze(
                tempDir,
                List.of(
                        tempDir.resolve("src/main/java/org/apache/ibatis/annotations/Mapper.java"),
                        tempDir.resolve("src/main/java/com/acme/UserMapper.java")));
        MyBatisMapperAnalysisResult mappers = MyBatisMapperAnalyzer.defaults().analyze(source);
        MyBatisXmlAnalysisResult xml = MyBatisXmlAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/resources/com/acme/UserMapper.xml")));

        JavaSourceFactBatch batch = MyBatisMapperFactMapper.defaults().map(
                mappers,
                xml,
                new MyBatisFactContext(
                        "shop",
                        "_root",
                        "src/main/java",
                        "src/main/resources",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/resources/com/acme/UserMapper.xml"));

        assertTrue(batch.facts().isEmpty());
        assertTrue(batch.evidence().isEmpty());
    }

    private static void assertFact(
            JavaSourceFactBatch batch,
            String relation,
            String source,
            String target) {
        assertTrue(batch.facts().stream().anyMatch(fact -> fact.relationType().name().equals(relation)
                && fact.sourceIdentityId().equals(source)
                && fact.targetIdentityId().equals(target)), relation + " " + source + " -> " + target);
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
