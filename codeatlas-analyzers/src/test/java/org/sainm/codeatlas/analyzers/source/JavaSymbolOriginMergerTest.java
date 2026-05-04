package org.sainm.codeatlas.analyzers.source;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sainm.codeatlas.analyzers.bytecode.BytecodeAnalysisResult;
import org.sainm.codeatlas.analyzers.bytecode.BytecodeClassInfo;
import org.sainm.codeatlas.analyzers.bytecode.BytecodeFieldInfo;
import org.sainm.codeatlas.analyzers.bytecode.BytecodeMethodInfo;

class JavaSymbolOriginMergerTest {
    @Test
    void mergesSourceOnlyJarOnlyAndSourcePlusJvmSymbols() {
        JavaSourceAnalysisResult source = new JavaSourceAnalysisResult(
                false,
                List.of(
                        new JavaClassInfo("com.acme.Shared", "Shared", List.of(), location()),
                        new JavaClassInfo("com.acme.SourceOnly", "SourceOnly", List.of(), location())),
                List.of(
                        new JavaMethodInfo("com.acme.Shared", "run", "()V", "void", List.of(), List.of(), location()),
                        new JavaMethodInfo("com.acme.SourceOnly", "source", "()V", "void", List.of(), List.of(), location())),
                List.of(new JavaFieldInfo("com.acme.Shared", "name", "java.lang.String", "Ljava/lang/String;", List.of(), location())),
                List.of(),
                List.of());
        BytecodeAnalysisResult bytecode = new BytecodeAnalysisResult(
                List.of(
                        new BytecodeClassInfo("com.acme.Shared", "java.lang.Object", List.of(), List.of(), "app.jar"),
                        new BytecodeClassInfo("com.acme.JarOnly", "java.lang.Object", List.of(), List.of(), "app.jar")),
                List.of(
                        new BytecodeMethodInfo("com.acme.Shared", "run", "()V", List.of(), "app.jar"),
                        new BytecodeMethodInfo("com.acme.JarOnly", "jar", "()V", List.of(), "app.jar")),
                List.of(new BytecodeFieldInfo("com.acme.Shared", "name", "java.lang.String", "Ljava/lang/String;", List.of(), "app.jar")),
                List.of());

        JavaSymbolOriginMergeResult result = JavaSymbolOriginMerger.defaults().merge(source, bytecode);

        assertMerged(result, JavaMergedSymbolKind.CLASS, "com.acme.Shared", false, false);
        assertMerged(result, JavaMergedSymbolKind.CLASS, "com.acme.SourceOnly", true, false);
        assertMerged(result, JavaMergedSymbolKind.CLASS, "com.acme.JarOnly", false, true);
        assertMerged(result, JavaMergedSymbolKind.METHOD, "com.acme.Shared#run()V", false, false);
        assertMerged(result, JavaMergedSymbolKind.METHOD, "com.acme.SourceOnly#source()V", true, false);
        assertMerged(result, JavaMergedSymbolKind.METHOD, "com.acme.JarOnly#jar()V", false, true);
        assertMerged(result, JavaMergedSymbolKind.FIELD, "com.acme.Shared#name:Ljava/lang/String;", false, false);
    }

    @Test
    void preservesOverloadedMethodsWhenMergingOrigins() {
        JavaSourceAnalysisResult source = new JavaSourceAnalysisResult(
                false,
                List.of(new JavaClassInfo("com.acme.Overloaded", "Overloaded", List.of(), location())),
                List.of(
                        new JavaMethodInfo("com.acme.Overloaded", "run", "(Ljava/lang/String;)V", "void",
                                List.of(), List.of(), location()),
                        new JavaMethodInfo("com.acme.Overloaded", "run", "(I)V", "void",
                                List.of(), List.of(), location())),
                List.of(),
                List.of(),
                List.of());
        BytecodeAnalysisResult bytecode = new BytecodeAnalysisResult(
                List.of(new BytecodeClassInfo("com.acme.Overloaded", "java.lang.Object", List.of(), List.of(), "app.jar")),
                List.of(
                        new BytecodeMethodInfo("com.acme.Overloaded", "run", "(Ljava/lang/String;)V", List.of(), "app.jar"),
                        new BytecodeMethodInfo("com.acme.Overloaded", "run", "(I)V", List.of(), "app.jar")),
                List.of(),
                List.of());

        JavaSymbolOriginMergeResult result = JavaSymbolOriginMerger.defaults().merge(source, bytecode);

        assertMerged(result, JavaMergedSymbolKind.METHOD, "com.acme.Overloaded#run(Ljava/lang/String;)V", false, false);
        assertMerged(result, JavaMergedSymbolKind.METHOD, "com.acme.Overloaded#run(I)V", false, false);
    }

    @Test
    void preservesFieldTypeChangesWhenMergingOrigins() {
        JavaSourceAnalysisResult source = new JavaSourceAnalysisResult(
                false,
                List.of(new JavaClassInfo("com.acme.ChangedField", "ChangedField", List.of(), location())),
                List.of(),
                List.of(new JavaFieldInfo("com.acme.ChangedField", "value", "java.lang.String", "Ljava/lang/String;", List.of(), location())),
                List.of(),
                List.of());
        BytecodeAnalysisResult bytecode = new BytecodeAnalysisResult(
                List.of(new BytecodeClassInfo("com.acme.ChangedField", "java.lang.Object", List.of(), List.of(), "app.jar")),
                List.of(),
                List.of(new BytecodeFieldInfo("com.acme.ChangedField", "value", "int", "I", List.of(), "app.jar")),
                List.of());

        JavaSymbolOriginMergeResult result = JavaSymbolOriginMerger.defaults().merge(source, bytecode);

        assertMerged(result, JavaMergedSymbolKind.FIELD, "com.acme.ChangedField#value:Ljava/lang/String;", true, false);
        assertMerged(result, JavaMergedSymbolKind.FIELD, "com.acme.ChangedField#value:I", false, true);
    }

    private static void assertMerged(
            JavaSymbolOriginMergeResult result,
            JavaMergedSymbolKind kind,
            String stableKey,
            boolean sourceOnly,
            boolean jvmOnly) {
        JavaMergedSymbol symbol = result.requireSymbol(kind, stableKey);
        if (sourceOnly) {
            assertTrue(symbol.sourceOnly());
        } else {
            assertFalse(symbol.sourceOnly());
        }
        if (jvmOnly) {
            assertTrue(symbol.jvmOnly());
        } else {
            assertFalse(symbol.jvmOnly());
        }
    }

    private static SourceLocation location() {
        return new SourceLocation("src/main/java/com/acme/Shared.java", 1, 1);
    }
}
