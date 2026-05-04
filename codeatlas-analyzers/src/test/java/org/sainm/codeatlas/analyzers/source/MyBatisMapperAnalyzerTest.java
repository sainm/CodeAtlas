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

class MyBatisMapperAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void discoversAnnotatedMapperInterfacesAndMethods() throws IOException {
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
                    default String helper() { return "ignored"; }
                    static String tableName() { return "users"; }
                }

                interface HelperMapper {
                    void ignored();
                }

                @Mapper
                class NotAnInterface {
                    void ignored() {}
                }

                class User {}
                """);

        JavaSourceAnalysisResult source = JavaSourceAnalyzer.defaults().analyze(
                tempDir,
                List.of(
                        tempDir.resolve("src/main/java/org/apache/ibatis/annotations/Mapper.java"),
                        tempDir.resolve("src/main/java/com/acme/UserMapper.java")));

        MyBatisMapperAnalysisResult result = MyBatisMapperAnalyzer.defaults().analyze(source);

        assertEquals(1, result.interfaces().size());
        assertTrue(result.interfaces().stream().anyMatch(mapper -> mapper.qualifiedName().equals("com.acme.UserMapper")
                && mapper.location().relativePath().equals("src/main/java/com/acme/UserMapper.java")));
        assertEquals(2, result.methods().size());
        assertTrue(result.methods().stream().anyMatch(method -> method.ownerQualifiedName().equals("com.acme.UserMapper")
                && method.simpleName().equals("findById")
                && method.signature().equals("(Ljava/lang/String;)Lcom/acme/User;")));
        assertTrue(result.methods().stream().anyMatch(method -> method.ownerQualifiedName().equals("com.acme.UserMapper")
                && method.simpleName().equals("updateName")
                && method.signature().equals("(Ljava/lang/String;Ljava/lang/String;)I")));
    }

    @Test
    void discoversMappersWhenMyBatisAnnotationDependencyIsMissing() throws IOException {
        write("src/main/java/com/acme/AccountMapper.java", """
                package com.acme;

                import org.apache.ibatis.annotations.Mapper;

                @Mapper
                interface AccountMapper {
                    Account find(String id);
                }

                class Account {}
                """);

        JavaSourceAnalysisResult source = JavaSourceAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/java/com/acme/AccountMapper.java")));

        MyBatisMapperAnalysisResult result = MyBatisMapperAnalyzer.defaults().analyze(source);

        assertTrue(source.noClasspathFallbackUsed());
        assertEquals(1, result.interfaces().size());
        assertTrue(result.interfaces().stream()
                .anyMatch(mapper -> mapper.qualifiedName().equals("com.acme.AccountMapper")));
        assertEquals(1, result.methods().size());
        assertTrue(result.methods().stream().anyMatch(method -> method.ownerQualifiedName().equals("com.acme.AccountMapper")
                && method.simpleName().equals("find")
                && method.signature().equals("(Ljava/lang/String;)Lcom/acme/Account;")));
    }

    @Test
    void discoversMapperInterfacesFromXmlNamespaces() throws IOException {
        write("src/main/java/com/acme/UserMapper.java", """
                package com.acme;

                interface UserMapper {
                    User find(String id);
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
                List.of(tempDir.resolve("src/main/java/com/acme/UserMapper.java")));
        MyBatisXmlAnalysisResult xml = MyBatisXmlAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/resources/com/acme/UserMapper.xml")));

        MyBatisMapperAnalysisResult result = MyBatisMapperAnalyzer.defaults().analyze(source, xml);

        assertEquals(1, result.interfaces().size());
        assertTrue(result.interfaces().stream()
                .anyMatch(mapper -> mapper.qualifiedName().equals("com.acme.UserMapper")));
        assertEquals(1, result.methods().size());
        assertTrue(result.methods().stream().anyMatch(method -> method.ownerQualifiedName().equals("com.acme.UserMapper")
                && method.simpleName().equals("find")
                && method.signature().equals("(Ljava/lang/String;)Lcom/acme/User;")));
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
