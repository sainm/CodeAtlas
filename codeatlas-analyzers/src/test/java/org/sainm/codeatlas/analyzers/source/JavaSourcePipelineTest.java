package org.sainm.codeatlas.analyzers.source;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class JavaSourcePipelineTest {
    @Test
    void combinesFactsFromMultipleMappers() {
        JavaMethodInfo nativeMethod = new JavaMethodInfo(
                "com.acme.NativeBridge",
                "nativeInit",
                "()V",
                "void",
                List.of(),
                List.of("public", "native"),
                false,
                new SourceLocation("src/main/java/com/acme/NativeBridge.java", 12, 5));

        JavaSourceAnalysisResult result = new JavaSourceAnalysisResult(
                false,
                List.of(new JavaClassInfo("com.acme.NativeBridge", "NativeBridge", JavaTypeKind.CLASS,
                        List.of(), new SourceLocation("src/main/java/com/acme/NativeBridge.java", 10, 5))),
                List.of(nativeMethod),
                List.of(),
                List.of(),
                List.of());

        JavaSourceFactContext context = new JavaSourceFactContext(
                "shop", "_root", "src/main/java",
                "snap-1", "analysis-1", "scope-1",
                "src/main/java/com/acme/NativeBridge.java");

        JavaSourceFactBatch batch = JavaSourcePipeline.defaults().map(result, context);

        assertTrue(batch.facts().stream().anyMatch(
                f -> f.relationType().name().equals("DECLARES")
                        && f.targetIdentityId().contains("NativeBridge")));
        assertTrue(batch.facts().stream().anyMatch(
                f -> f.relationType().name().equals("HAS_NATIVE_BOUNDARY")));
        assertTrue(batch.facts().stream().anyMatch(
                f -> f.relationType().name().equals("CALLS_NATIVE")));
    }

    @Test
    void regularMethodProducesOnlyJavaSourceFacts() {
        JavaMethodInfo regularMethod = new JavaMethodInfo(
                "com.acme.Service",
                "process",
                "()V",
                "void",
                List.of(),
                List.of("public"),
                true,
                new SourceLocation("src/main/java/com/acme/Service.java", 8, 5));

        JavaSourceAnalysisResult result = new JavaSourceAnalysisResult(
                false,
                List.of(new JavaClassInfo("com.acme.Service", "Service", JavaTypeKind.CLASS,
                        List.of(), new SourceLocation("src/main/java/com/acme/Service.java", 5, 5))),
                List.of(regularMethod),
                List.of(),
                List.of(),
                List.of());

        JavaSourceFactContext context = new JavaSourceFactContext(
                "shop", "_root", "src/main/java",
                "snap-1", "analysis-1", "scope-1",
                "src/main/java/com/acme/Service.java");

        JavaSourceFactBatch batch = JavaSourcePipeline.defaults().map(result, context);

        assertTrue(batch.facts().stream().anyMatch(
                f -> f.relationType().name().equals("DECLARES")));
        assertTrue(batch.facts().stream().noneMatch(
                f -> f.relationType().name().equals("HAS_NATIVE_BOUNDARY")));
    }
}
