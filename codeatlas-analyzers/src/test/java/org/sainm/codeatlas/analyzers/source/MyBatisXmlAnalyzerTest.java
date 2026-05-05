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

class MyBatisXmlAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsMapperNamespaceAndStatementIds() throws IOException {
        write("src/main/resources/com/acme/UserMapper.xml", """
                <?xml version="1.0" encoding="UTF-8" ?>
                <!DOCTYPE mapper
                  PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
                  "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
                <mapper namespace="com.acme.UserMapper">
                  <select id="findById" resultType="com.acme.User">
                    select * from users where id = #{id}
                  </select>
                  <update id="updateName">
                    update users set name = #{name} where id = #{id}
                  </update>
                  <sql id="BaseColumns">id, name</sql>
                </mapper>
                """);

        MyBatisXmlAnalysisResult result = MyBatisXmlAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/resources/com/acme/UserMapper.xml")));

        assertTrue(result.diagnostics().isEmpty());
        assertEquals(1, result.mappers().size());
        assertTrue(result.mappers().stream().anyMatch(mapper -> mapper.namespace().equals("com.acme.UserMapper")
                && mapper.path().equals("src/main/resources/com/acme/UserMapper.xml")));
        assertEquals(2, result.statements().size());
        assertTrue(result.statements().stream().anyMatch(statement -> statement.namespace().equals("com.acme.UserMapper")
                && statement.id().equals("findById")
                && statement.kind() == MyBatisStatementKind.SELECT));
        assertTrue(result.statements().stream().anyMatch(statement -> statement.namespace().equals("com.acme.UserMapper")
                && statement.id().equals("updateName")
                && statement.kind() == MyBatisStatementKind.UPDATE));
    }

    @Test
    void recordsDiagnosticsForMalformedMapperXml() throws IOException {
        write("src/main/resources/com/acme/BrokenMapper.xml", """
                <mapper namespace="com.acme.BrokenMapper">
                  <select id="find">
                </mapper>
                """);

        MyBatisXmlAnalysisResult result = MyBatisXmlAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/resources/com/acme/BrokenMapper.xml")));

        assertTrue(result.mappers().isEmpty());
        assertTrue(result.statements().isEmpty());
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("MYBATIS_XML_PARSE_FAILED")));
    }

    @Test
    void exposesDynamicSqlForConservativeTableAnalysis() throws IOException {
        write("src/main/resources/com/acme/UserMapper.xml", """
                <mapper namespace="com.acme.UserMapper">
                  <select id="search">
                    select u.id
                    from users u
                    <where>
                      <if test="name != null">
                        and u.name = #{name}
                      </if>
                    </where>
                  </select>
                </mapper>
                """);

        MyBatisXmlAnalysisResult xml = MyBatisXmlAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/resources/com/acme/UserMapper.xml")));
        SqlTableAnalysisResult tables = SqlTableAnalyzer.defaults().analyze(xml.sqlStatementSources());

        assertTrue(tables.diagnostics().isEmpty());
        assertTrue(tables.tableAccesses().stream().anyMatch(access -> access.statementId().equals("com.acme.UserMapper.search")
                && access.tableName().equals("users")
                && access.kind() == SqlTableAccessKind.READ
                && access.conservativeFallback()));
    }

    @Test
    void expandsIncludedSqlFragmentsForTableAnalysis() throws IOException {
        write("src/main/resources/com/acme/UserMapper.xml", """
                <mapper namespace="com.acme.UserMapper">
                  <sql id="fromUsers">from users</sql>
                  <select id="find">
                    select *
                    <include refid="fromUsers"/>
                  </select>
                </mapper>
                """);

        MyBatisXmlAnalysisResult xml = MyBatisXmlAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/resources/com/acme/UserMapper.xml")));
        SqlTableAnalysisResult tables = SqlTableAnalyzer.defaults().analyze(xml.sqlStatementSources());

        assertTrue(tables.diagnostics().isEmpty());
        assertTrue(tables.tableAccesses().stream().anyMatch(access -> access.statementId().equals("com.acme.UserMapper.find")
                && access.tableName().equals("users")
                && access.kind() == SqlTableAccessKind.READ));
    }

    @Test
    void expandsIncludedSqlFragmentsFromOtherMapperFiles() throws IOException {
        write("src/main/resources/com/acme/CommonMapper.xml", """
                <mapper namespace="com.acme.Common">
                  <sql id="fromUsers">from users</sql>
                </mapper>
                """);
        write("src/main/resources/com/acme/UserMapper.xml", """
                <mapper namespace="com.acme.UserMapper">
                  <select id="find">
                    select *
                    <include refid="com.acme.Common.fromUsers"/>
                  </select>
                </mapper>
                """);

        MyBatisXmlAnalysisResult xml = MyBatisXmlAnalyzer.defaults().analyze(
                tempDir,
                List.of(
                        tempDir.resolve("src/main/resources/com/acme/CommonMapper.xml"),
                        tempDir.resolve("src/main/resources/com/acme/UserMapper.xml")));
        SqlTableAnalysisResult tables = SqlTableAnalyzer.defaults().analyze(xml.sqlStatementSources());

        assertTrue(tables.diagnostics().isEmpty());
        assertTrue(tables.tableAccesses().stream().anyMatch(access -> access.statementId().equals("com.acme.UserMapper.find")
                && access.tableName().equals("users")
                && access.kind() == SqlTableAccessKind.READ));
    }

    @Test
    void prefersSameNamespaceSqlFragmentsForShortIncludes() throws IOException {
        write("src/main/resources/com/acme/OrderMapper.xml", """
                <mapper namespace="com.acme.OrderMapper">
                  <sql id="fromTable">from orders</sql>
                </mapper>
                """);
        write("src/main/resources/com/acme/UserMapper.xml", """
                <mapper namespace="com.acme.UserMapper">
                  <sql id="fromTable">from users</sql>
                  <select id="find">
                    select *
                    <include refid="fromTable"/>
                  </select>
                </mapper>
                """);

        MyBatisXmlAnalysisResult xml = MyBatisXmlAnalyzer.defaults().analyze(
                tempDir,
                List.of(
                        tempDir.resolve("src/main/resources/com/acme/OrderMapper.xml"),
                        tempDir.resolve("src/main/resources/com/acme/UserMapper.xml")));
        SqlTableAnalysisResult tables = SqlTableAnalyzer.defaults().analyze(xml.sqlStatementSources());

        assertTrue(tables.diagnostics().isEmpty());
        assertTrue(tables.tableAccesses().stream().anyMatch(access -> access.statementId().equals("com.acme.UserMapper.find")
                && access.tableName().equals("users")
                && access.kind() == SqlTableAccessKind.READ));
        assertTrue(tables.tableAccesses().stream().noneMatch(access -> access.statementId().equals("com.acme.UserMapper.find")
                && access.tableName().equals("orders")));
    }

    @Test
    void doesNotResolveUnqualifiedIncludesFromOtherNamespaces() throws IOException {
        write("src/main/resources/com/acme/CommonMapper.xml", """
                <mapper namespace="com.acme.CommonMapper">
                  <sql id="fromTable">from orders</sql>
                </mapper>
                """);
        write("src/main/resources/com/acme/UserMapper.xml", """
                <mapper namespace="com.acme.UserMapper">
                  <select id="find">
                    select *
                    <include refid="fromTable"/>
                  </select>
                </mapper>
                """);

        MyBatisXmlAnalysisResult xml = MyBatisXmlAnalyzer.defaults().analyze(
                tempDir,
                List.of(
                        tempDir.resolve("src/main/resources/com/acme/CommonMapper.xml"),
                        tempDir.resolve("src/main/resources/com/acme/UserMapper.xml")));
        SqlTableAnalysisResult tables = SqlTableAnalyzer.defaults().analyze(xml.sqlStatementSources());

        assertTrue(tables.tableAccesses().stream().noneMatch(access -> access.statementId().equals("com.acme.UserMapper.find")
                && access.tableName().equals("orders")));
    }

    @Test
    void marksParseableDynamicSqlTableAccessesAsConservative() throws IOException {
        write("src/main/resources/com/acme/UserMapper.xml", """
                <mapper namespace="com.acme.UserMapper">
                  <select id="search">
                    select u.id
                    from users u
                    <if test="includeOrders">
                      join orders o on o.user_id = u.id
                    </if>
                  </select>
                </mapper>
                """);

        MyBatisXmlAnalysisResult xml = MyBatisXmlAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/resources/com/acme/UserMapper.xml")));
        SqlTableAnalysisResult tables = SqlTableAnalyzer.defaults().analyze(xml.sqlStatementSources());

        assertTrue(tables.diagnostics().isEmpty());
        assertTrue(tables.tableAccesses().stream().anyMatch(access -> access.statementId().equals("com.acme.UserMapper.search")
                && access.tableName().equals("orders")
                && access.kind() == SqlTableAccessKind.READ
                && access.conservativeFallback()));
    }

    @Test
    void recordsStatementLineNumbers() throws IOException {
        write("src/main/resources/com/acme/UserMapper.xml", """
                <mapper namespace="com.acme.UserMapper">

                  <select id="find">
                    select * from users
                  </select>

                  <update id="updateName">
                    update users set name = #{name}
                  </update>
                </mapper>
                """);

        MyBatisXmlAnalysisResult xml = MyBatisXmlAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/resources/com/acme/UserMapper.xml")));

        assertTrue(xml.statements().stream().anyMatch(statement -> statement.id().equals("find")
                && statement.location().line() == 3));
        assertTrue(xml.statements().stream().anyMatch(statement -> statement.id().equals("updateName")
                && statement.location().line() == 7));
    }

    @Test
    void recordsStatementLineNumbersForMultilineStartTags() throws IOException {
        write("src/main/resources/com/acme/UserMapper.xml", """
                <mapper namespace="com.acme.UserMapper">
                  <select
                      id="find"
                      resultType="com.acme.User">
                    select * from users
                  </select>
                </mapper>
                """);

        MyBatisXmlAnalysisResult xml = MyBatisXmlAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/resources/com/acme/UserMapper.xml")));

        assertTrue(xml.statements().stream().anyMatch(statement -> statement.id().equals("find")
                && statement.location().line() == 2
                && statement.location().column() == 3));
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
