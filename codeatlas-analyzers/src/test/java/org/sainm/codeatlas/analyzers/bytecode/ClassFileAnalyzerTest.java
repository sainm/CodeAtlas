package org.sainm.codeatlas.analyzers.bytecode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SymbolKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClassFileAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsMethodCallsFromBusinessJarBytecode() throws Exception {
        Path jar = compileBusinessJar();
        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "project", tempDir);

        ClassFileAnalysisResult result = new ClassFileAnalyzer().analyze(scope, "shop", "src/main/java", List.of(jar));

        String apply = "method://shop/_root/src/main/java/com.vendor.logic.PricingLogic#apply(java.lang.String):void";
        String normalize = "method://shop/_root/src/main/java/com.vendor.logic.PricingUtil#normalize(java.lang.String):java.lang.String";
        String save = "method://shop/_root/src/main/java/com.vendor.logic.PricingDao#save(java.lang.String):void";
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().value().equals(apply)));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().value().equals(normalize)));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().value().equals(save)));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.CALLS
            && fact.factKey().source().value().equals(apply)
            && fact.factKey().target().value().equals(normalize)
            && fact.factKey().qualifier().equals("bytecode-static")
            && fact.confidence() == Confidence.CERTAIN));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.CALLS
            && fact.factKey().source().value().equals(apply)
            && fact.factKey().target().value().equals(save)
            && fact.factKey().qualifier().equals("bytecode-virtual")
            && fact.confidence() == Confidence.LIKELY));
    }

    @Test
    void keepsInheritanceAndInterfaceEdgesFromClassFiles() throws Exception {
        Path jar = compileBusinessJar();
        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "project", tempDir);

        ClassFileAnalysisResult result = new ClassFileAnalyzer().analyze(scope, "shop", "src/main/java", List.of(jar));

        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.INTERFACE
            && node.symbolId().ownerQualifiedName().equals("com.vendor.logic.BusinessLogic")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.ENUM
            && node.symbolId().ownerQualifiedName().equals("com.vendor.logic.PriceStatus")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.ANNOTATION
            && node.symbolId().ownerQualifiedName().equals("com.vendor.logic.LegacyMarker")));
        assertEquals(1, result.facts().stream()
            .filter(fact -> fact.factKey().relationType() == RelationType.IMPLEMENTS
                && fact.factKey().source().ownerQualifiedName().equals("com.vendor.logic.PricingLogic")
                && fact.factKey().target().ownerQualifiedName().equals("com.vendor.logic.BusinessLogic"))
            .count());
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.CLASS
            && node.symbolId().ownerQualifiedName().equals("com.vendor.logic.PricingLogic")
            && node.properties().getOrDefault("annotations", "").contains("com.vendor.logic.LegacyMarker")));
    }

    @Test
    void marksJvmOnlySyntheticAndBridgeMethodsFromClassFiles() throws Exception {
        Path jar = compileBusinessJar();
        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "project", tempDir);

        ClassFileAnalysisResult result = new ClassFileAnalyzer().analyze(scope, "shop", "src/main/java", List.of(jar));

        String bridgeMethod = "method://shop/_root/src/main/java/com.vendor.logic.StringConverter#convert(java.lang.Object):java.lang.Object";
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().value().equals(bridgeMethod)
            && node.properties().get("codeOrigin").equals("jvm")
            && node.properties().get("jvmOnly").equals("true")
            && node.properties().get("synthetic").equals("true")
            && node.properties().get("bridge").equals("true")));
    }

    @Test
    void extractsFieldsAndDefaultJpaMappingFromBusinessJarClassFiles() throws Exception {
        Path jar = compileBusinessJar();
        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "project", tempDir);

        ClassFileAnalysisResult result = new ClassFileAnalyzer().analyze(scope, "shop", "src/main/java", List.of(jar));

        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.FIELD
            && node.symbolId().ownerQualifiedName().equals("com.vendor.logic.UserEntity")
            && node.symbolId().memberName().equals("name")
            && node.properties().get("codeOrigin").equals("jvm")
            && node.properties().get("static").equals("false")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.FIELD
            && node.symbolId().ownerQualifiedName().equals("com.vendor.logic.UserEntity")
            && node.symbolId().memberName().equals("TYPE")
            && node.properties().get("static").equals("true")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.BINDS_TO
            && fact.factKey().source().kind() == SymbolKind.CLASS
            && fact.factKey().source().ownerQualifiedName().equals("com.vendor.logic.UserEntity")
            && fact.factKey().target().kind() == SymbolKind.DB_TABLE
            && fact.factKey().target().ownerQualifiedName().equals("users")
            && fact.factKey().qualifier().equals("classfile-jpa-entity-table:users")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.BINDS_TO
            && fact.factKey().source().kind() == SymbolKind.FIELD
            && fact.factKey().source().memberName().equals("name")
            && fact.factKey().target().kind() == SymbolKind.DB_COLUMN
            && fact.factKey().target().ownerQualifiedName().equals("users")
            && fact.factKey().target().localId().equals("user_name")
            && fact.confidence() == Confidence.LIKELY));
        assertTrue(result.facts().stream().noneMatch(fact -> fact.factKey().source().kind() == SymbolKind.FIELD
            && fact.factKey().source().ownerQualifiedName().equals("com.vendor.logic.UserEntity")
            && fact.factKey().source().memberName().equals("temporaryToken")
            && fact.factKey().relationType() == RelationType.BINDS_TO));
        assertTrue(result.facts().stream().noneMatch(fact -> fact.factKey().source().kind() == SymbolKind.FIELD
            && fact.factKey().source().ownerQualifiedName().equals("com.vendor.logic.UserEntity")
            && fact.factKey().source().memberName().equals("TYPE")
            && fact.factKey().relationType() == RelationType.BINDS_TO));
    }

    @Test
    void extractsSpringEndpointFactsFromBusinessJarMethodAnnotations() throws Exception {
        Path jar = compileBusinessJar();
        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "project", tempDir);

        ClassFileAnalysisResult result = new ClassFileAnalyzer().analyze(scope, "shop", "src/main/java", List.of(jar));

        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.CLASS
            && node.symbolId().ownerQualifiedName().equals("com.vendor.logic.JarUserController")
            && node.roles().contains(org.sainm.codeatlas.graph.model.NodeRole.CONTROLLER)));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.ROUTES_TO
            && fact.factKey().source().kind() == SymbolKind.API_ENDPOINT
            && fact.factKey().source().ownerQualifiedName().equals("jar-users/{id}")
            && fact.factKey().source().localId().equals("GET")
            && fact.factKey().target().kind() == SymbolKind.METHOD
            && fact.factKey().target().ownerQualifiedName().equals("com.vendor.logic.JarUserController")
            && fact.factKey().target().memberName().equals("find")
            && fact.factKey().qualifier().equals("classfile-spring-endpoint:GET")
            && fact.confidence() == Confidence.LIKELY));
    }

    @Test
    void indexesClasspathResourceFilesFromBusinessJars() throws Exception {
        Path jar = compileBusinessJar();
        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "project", tempDir);

        ClassFileAnalysisResult result = new ClassFileAnalyzer().analyze(scope, "shop", "src/main/java", List.of(jar));

        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.CONFIG_KEY
            && node.symbolId().ownerQualifiedName().endsWith("pricing-logic.jar")
            && node.symbolId().localId().equals("resource:META-INF/spring.factories")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.CONFIG_KEY
            && node.symbolId().ownerQualifiedName().endsWith("pricing-logic.jar")
            && node.symbolId().localId().equals("resource:legacy/plugin-init.xml")));
        assertTrue(result.nodes().stream().noneMatch(node -> node.symbolId().kind() == SymbolKind.CONFIG_KEY
            && node.symbolId().localId().equals("resource:assets/logo.png")));
    }

    private Path compileBusinessJar() throws Exception {
        Path source = tempDir.resolve("business-jar-src/com/vendor/logic/PricingLogic.java");
        Files.createDirectories(source.getParent());
        Path springAnnotationRoot = tempDir.resolve("business-jar-src/org/springframework/web/bind/annotation");
        Files.createDirectories(springAnnotationRoot);
        Files.writeString(springAnnotationRoot.resolve("RestController.java"), """
            package org.springframework.web.bind.annotation;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            @Retention(RetentionPolicy.RUNTIME)
            public @interface RestController {
            }
            """);
        Files.writeString(springAnnotationRoot.resolve("RequestMapping.java"), """
            package org.springframework.web.bind.annotation;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            @Retention(RetentionPolicy.RUNTIME)
            public @interface RequestMapping {
              String[] value() default {};
              String[] path() default {};
              RequestMethod[] method() default {};
            }
            """);
        Files.writeString(springAnnotationRoot.resolve("GetMapping.java"), """
            package org.springframework.web.bind.annotation;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            @Retention(RetentionPolicy.RUNTIME)
            public @interface GetMapping {
              String[] value() default {};
              String[] path() default {};
            }
            """);
        Files.writeString(springAnnotationRoot.resolve("RequestMethod.java"), """
            package org.springframework.web.bind.annotation;
            public enum RequestMethod {
              GET, POST, PUT, DELETE, PATCH
            }
            """);
        Files.writeString(source, """
            package com.vendor.logic;
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RestController;
            @interface Entity {
            }
            @interface Table {
              String name() default "";
              String value() default "";
            }
            @interface Column {
              String name() default "";
              String value() default "";
            }
            @interface Transient {
            }
            interface BusinessLogic {
              void apply(String userId);
            }
            @interface LegacyMarker {
            }
            enum PriceStatus {
              ACTIVE
            }
            @LegacyMarker
            public class PricingLogic implements BusinessLogic {
              private final PricingDao dao = new PricingDao();
              public void apply(String userId) {
                String normalized = PricingUtil.normalize(userId);
                dao.save(normalized);
              }
            }
            class PricingDao {
              void save(String userId) {
              }
            }
            class PricingUtil {
              static String normalize(String value) {
                return value;
              }
            }
            interface Converter<T> {
              T convert(T value);
            }
            class StringConverter implements Converter<String> {
              public String convert(String value) {
                return value;
              }
            }
            @Entity
            @Table(name = "users")
            class UserEntity {
              @Column(name = "user_name")
              String name;
              @Transient
              String temporaryToken;
              static String TYPE = "USER";
            }
            @RestController
            @RequestMapping(path = "/jar-users")
            class JarUserController {
              @GetMapping(path = "/{id}")
              String find(String id) {
                return id;
              }
            }
            """);
        Path classes = tempDir.resolve("business-jar-classes");
        Files.createDirectories(classes);
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertTrue(compiler != null, "JDK compiler is required for classfile fixture");
        int exitCode = compiler.run(
            null,
            null,
            null,
            "-d",
            classes.toString(),
            source.toString(),
            springAnnotationRoot.resolve("RestController.java").toString(),
            springAnnotationRoot.resolve("RequestMapping.java").toString(),
            springAnnotationRoot.resolve("GetMapping.java").toString(),
            springAnnotationRoot.resolve("RequestMethod.java").toString()
        );
        assertEquals(0, exitCode, "business jar fixture compilation failed");

        Path jar = tempDir.resolve("src/main/webapp/WEB-INF/lib/pricing-logic.jar");
        Files.createDirectories(jar.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            addClass(output, classes, "com/vendor/logic/BusinessLogic.class");
            addClass(output, classes, "com/vendor/logic/LegacyMarker.class");
            addClass(output, classes, "com/vendor/logic/PriceStatus.class");
            addClass(output, classes, "com/vendor/logic/PricingLogic.class");
            addClass(output, classes, "com/vendor/logic/PricingDao.class");
            addClass(output, classes, "com/vendor/logic/PricingUtil.class");
            addClass(output, classes, "com/vendor/logic/Converter.class");
            addClass(output, classes, "com/vendor/logic/StringConverter.class");
            addClass(output, classes, "com/vendor/logic/Entity.class");
            addClass(output, classes, "com/vendor/logic/Table.class");
            addClass(output, classes, "com/vendor/logic/Column.class");
            addClass(output, classes, "com/vendor/logic/Transient.class");
            addClass(output, classes, "com/vendor/logic/UserEntity.class");
            addClass(output, classes, "com/vendor/logic/JarUserController.class");
            addClass(output, classes, "org/springframework/web/bind/annotation/RestController.class");
            addClass(output, classes, "org/springframework/web/bind/annotation/RequestMapping.class");
            addClass(output, classes, "org/springframework/web/bind/annotation/GetMapping.class");
            addClass(output, classes, "org/springframework/web/bind/annotation/RequestMethod.class");
            addResource(output, "META-INF/spring.factories", "com.vendor.logic.Plugin=true");
            addResource(output, "legacy/plugin-init.xml", "<plugin/>");
            addResource(output, "assets/logo.png", "not-indexed");
        }
        return jar;
    }

    private static void addClass(JarOutputStream output, Path classes, String entryName) throws Exception {
        output.putNextEntry(new JarEntry(entryName));
        Files.copy(classes.resolve(entryName), output);
        output.closeEntry();
    }

    private static void addResource(JarOutputStream output, String entryName, String content) throws Exception {
        output.putNextEntry(new JarEntry(entryName));
        output.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        output.closeEntry();
    }
}
