package org.sainm.codeatlas.analyzers.source;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class NativeMethodFactMapperTest {
    private final JavaSourceFactContext context = new JavaSourceFactContext(
            "shop", "_root", "src/main/java",
            "snapshot-1", "analysis-1", "scope-1", "src/main/java");

    @Test
    void mapsNativeMethodsToBoundaryAndLibraryFacts() {
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
                List.of(new JavaClassInfo("com.acme.NativeBridge", "class", JavaTypeKind.CLASS,
                        List.of(), new SourceLocation("src/main/java/com/acme/NativeBridge.java", 10, 5))),
                List.of(nativeMethod),
                List.of(),
                List.of(),
                List.of());

        JavaSourceFactBatch batch = NativeMethodFactMapper.defaults().map(result, context);

        assertTrue(batch.facts().stream().anyMatch(
                fact -> fact.relationType().name().equals("HAS_NATIVE_BOUNDARY")
                        && fact.sourceIdentityId().equals("method://shop/_root/src/main/java/com.acme.NativeBridge#nativeInit()V")
                        && fact.targetIdentityId().startsWith("boundary-symbol://")
                        && fact.targetIdentityId().endsWith("@NATIVE")));

        assertTrue(batch.facts().stream().anyMatch(
                fact -> fact.relationType().name().equals("CALLS_NATIVE")
                        && fact.sourceIdentityId().equals("method://shop/_root/src/main/java/com.acme.NativeBridge#nativeInit()V")
                        && fact.targetIdentityId().startsWith("native-library://")
                        && fact.targetIdentityId().contains("NativeBridge")));
    }

    @Test
    void ignoresNonNativeMethods() {
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
                List.of(new JavaClassInfo("com.acme.Service", "class", JavaTypeKind.CLASS,
                        List.of(), new SourceLocation("src/main/java/com/acme/Service.java", 5, 5))),
                List.of(regularMethod),
                List.of(),
                List.of(),
                List.of());

        JavaSourceFactBatch batch = NativeMethodFactMapper.defaults().map(result, context);

        assertTrue(batch.facts().isEmpty());
    }

    @Test
    void mapsSystemLoadLibraryInvocationsToNativeLibraryFacts() {
        JavaMethodInfo method = new JavaMethodInfo(
                "com.acme.Bridge",
                "init",
                "()V",
                "void",
                List.of(),
                List.of("public"),
                true,
                0,
                new SourceLocation("src/main/java/com/acme/Bridge.java", 15, 5));

        JavaInvocationInfo loadLibraryCall = new JavaInvocationInfo(
                "com.acme.Bridge",
                "init",
                "()V",
                "java.lang.System",
                "loadLibrary",
                "(Ljava/lang/String;)V",
                new SourceLocation("src/main/java/com/acme/Bridge.java", 16, 13));

        JavaSourceAnalysisResult result = new JavaSourceAnalysisResult(
                false,
                List.of(new JavaClassInfo("com.acme.Bridge", "class", JavaTypeKind.CLASS,
                        List.of(), new SourceLocation("src/main/java/com/acme/Bridge.java", 10, 5))),
                List.of(method),
                List.of(),
                List.of(loadLibraryCall),
                List.of());

        JavaSourceFactBatch batch = NativeMethodFactMapper.defaults().map(result, context);

        assertTrue(batch.facts().stream().anyMatch(
                fact -> fact.relationType().name().equals("CALLS_NATIVE")
                        && fact.targetIdentityId().startsWith("native-library://")
                        && fact.targetIdentityId().contains("System_loadLibrary")));
    }

    @Test
    void mapsSystemLoadInvocationsToNativeLibraryFacts() {
        JavaMethodInfo method = new JavaMethodInfo(
                "com.acme.Bridge",
                "loadBridge",
                "()V",
                "void",
                List.of(),
                List.of("public"),
                true,
                0,
                new SourceLocation("src/main/java/com/acme/Bridge.java", 20, 5));

        JavaInvocationInfo systemLoadCall = new JavaInvocationInfo(
                "com.acme.Bridge",
                "loadBridge",
                "()V",
                "java.lang.System",
                "load",
                "(Ljava/lang/String;)V",
                new SourceLocation("src/main/java/com/acme/Bridge.java", 21, 13));

        JavaSourceAnalysisResult result = new JavaSourceAnalysisResult(
                false,
                List.of(new JavaClassInfo("com.acme.Bridge", "class", JavaTypeKind.CLASS,
                        List.of(), new SourceLocation("src/main/java/com/acme/Bridge.java", 10, 5))),
                List.of(method),
                List.of(),
                List.of(systemLoadCall),
                List.of());

        JavaSourceFactBatch batch = NativeMethodFactMapper.defaults().map(result, context);

        assertTrue(batch.facts().stream().anyMatch(
                fact -> fact.relationType().name().equals("CALLS_NATIVE")
                        && fact.targetIdentityId().contains("System_load")));
    }

    @Test
    void deduplicatesRepeatedFacts() {
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
                List.of(new JavaClassInfo("com.acme.NativeBridge", "class", JavaTypeKind.CLASS,
                        List.of(), new SourceLocation("src/main/java/com/acme/NativeBridge.java", 10, 5))),
                List.of(nativeMethod),
                List.of(),
                List.of(),
                List.of());

        JavaSourceFactBatch batch = NativeMethodFactMapper.defaults().map(result, context);

        long callNativeCount = batch.facts().stream()
                .filter(f -> f.relationType().name().equals("CALLS_NATIVE"))
                .count();
        long boundaryCount = batch.facts().stream()
                .filter(f -> f.relationType().name().equals("HAS_NATIVE_BOUNDARY"))
                .count();
        assertTrue(callNativeCount == 1, "Expected 1 CALLS_NATIVE fact but got " + callNativeCount);
        assertTrue(boundaryCount == 1, "Expected 1 HAS_NATIVE_BOUNDARY fact but got " + boundaryCount);
    }
}
