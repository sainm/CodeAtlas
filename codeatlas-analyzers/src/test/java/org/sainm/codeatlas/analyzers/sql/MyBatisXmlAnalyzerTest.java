package org.sainm.codeatlas.analyzers.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SymbolKind;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MyBatisXmlAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsStatementsAndTableFacts() throws Exception {
        Path xml = tempDir.resolve("src/main/resources/com/acme/UserMapper.xml");
        Files.createDirectories(xml.getParent());
        Files.writeString(xml, """
            <?xml version="1.0" encoding="UTF-8"?>
            <mapper namespace="com.acme.UserMapper">
              <select id="findById" resultType="User">
                select id, name from users where id = #{id}
              </select>
              <update id="rename">
                update users set name = #{name} where id = #{id}
              </update>
            </mapper>
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "src/main/resources", tempDir);
        MyBatisXmlAnalysisResult result = new MyBatisXmlAnalyzer().analyze(scope, "shop", "src/main/resources", xml);

        assertEquals(2, result.statements().size());
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.SQL_STATEMENT));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.DB_TABLE
            && node.symbolId().ownerQualifiedName().endsWith("users")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.DB_COLUMN
            && node.symbolId().ownerQualifiedName().endsWith("users")
            && node.symbolId().localId().equals("name")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.BINDS_TO));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.READS_TABLE));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.WRITES_TABLE));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().target().kind() == SymbolKind.DB_COLUMN));
    }

    @Test
    void marksDynamicSqlTableFactsAsPossible() throws Exception {
        Path xml = tempDir.resolve("src/main/resources/com/acme/UserMapper.xml");
        Files.createDirectories(xml.getParent());
        Files.writeString(xml, """
            <?xml version="1.0" encoding="UTF-8"?>
            <mapper namespace="com.acme.UserMapper">
              <select id="search" resultType="User">
                select id, name from users
                <if test="name != null">
                  where name = #{name}
                </if>
              </select>
            </mapper>
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "src/main/resources", tempDir);
        MyBatisXmlAnalysisResult result = new MyBatisXmlAnalyzer().analyze(scope, "shop", "src/main/resources", xml);

        assertTrue(result.statements().getFirst().dynamic());
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.READS_TABLE
            && fact.confidence() == Confidence.POSSIBLE));
    }

    @Test
    void usesJSqlParserForNestedSelectAndJoinTables() throws Exception {
        Path xml = tempDir.resolve("src/main/resources/com/acme/UserMapper.xml");
        Files.createDirectories(xml.getParent());
        Files.writeString(xml, """
            <?xml version="1.0" encoding="UTF-8"?>
            <mapper namespace="com.acme.UserMapper">
              <select id="findWithOrders" resultType="User">
                select u.id, o.total
                from users u
                join orders o on o.user_id = u.id
                where exists (
                  select 1 from audit_log a where a.user_id = u.id
                )
              </select>
            </mapper>
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "src/main/resources", tempDir);
        MyBatisXmlAnalysisResult result = new MyBatisXmlAnalyzer().analyze(scope, "shop", "src/main/resources", xml);

        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.DB_TABLE
            && node.symbolId().ownerQualifiedName().endsWith("users")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.DB_TABLE
            && node.symbolId().ownerQualifiedName().endsWith("orders")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.DB_TABLE
            && node.symbolId().ownerQualifiedName().endsWith("audit_log")));
        assertTrue(result.facts().stream().filter(fact -> fact.factKey().relationType() == RelationType.READS_TABLE).count() >= 3);
    }

    @Test
    void fallsBackForDynamicSqlThatJSqlParserCannotParse() throws Exception {
        Path xml = tempDir.resolve("src/main/resources/com/acme/UserMapper.xml");
        Files.createDirectories(xml.getParent());
        Files.writeString(xml, """
            <?xml version="1.0" encoding="UTF-8"?>
            <mapper namespace="com.acme.UserMapper">
              <select id="dynamicTable" resultType="User">
                select id, name from users order by ${orderBy}
              </select>
            </mapper>
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "src/main/resources", tempDir);
        MyBatisXmlAnalysisResult result = new MyBatisXmlAnalyzer().analyze(scope, "shop", "src/main/resources", xml);

        assertEquals(1, result.statements().size());
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.READS_TABLE));
    }

    @Test
    void toleratesMyBatisDoctypeWithoutLoadingExternalDtd() throws Exception {
        Path xml = tempDir.resolve("src/main/resources/com/acme/UserMapper.xml");
        Files.createDirectories(xml.getParent());
        Files.writeString(xml, """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
              "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
            <mapper namespace="com.acme.UserMapper">
              <select id="findAll" resultType="User">
                select id, name from users
              </select>
            </mapper>
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "src/main/resources", tempDir);
        MyBatisXmlAnalysisResult result = new MyBatisXmlAnalyzer().analyze(scope, "shop", "src/main/resources", xml);

        assertEquals(1, result.statements().size());
        assertEquals("findAll", result.statements().getFirst().id());
    }
}
