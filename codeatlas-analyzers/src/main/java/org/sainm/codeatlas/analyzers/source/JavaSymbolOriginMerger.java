package org.sainm.codeatlas.analyzers.source;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sainm.codeatlas.analyzers.bytecode.BytecodeAnalysisResult;

public final class JavaSymbolOriginMerger {
    private JavaSymbolOriginMerger() {
    }

    public static JavaSymbolOriginMerger defaults() {
        return new JavaSymbolOriginMerger();
    }

    public JavaSymbolOriginMergeResult merge(
            JavaSourceAnalysisResult source,
            BytecodeAnalysisResult bytecode) {
        if (source == null) {
            throw new IllegalArgumentException("source is required");
        }
        if (bytecode == null) {
            throw new IllegalArgumentException("bytecode is required");
        }
        Map<String, OriginFlags> flagsByKey = new LinkedHashMap<>();
        source.classes().forEach(type -> markSource(flagsByKey, JavaMergedSymbolKind.CLASS, type.qualifiedName()));
        source.methods().forEach(method -> markSource(flagsByKey, JavaMergedSymbolKind.METHOD,
                method.ownerQualifiedName() + "#" + method.simpleName()));
        source.fields().forEach(field -> markSource(flagsByKey, JavaMergedSymbolKind.FIELD,
                field.ownerQualifiedName() + "#" + field.simpleName()));

        bytecode.classes().forEach(type -> markJvm(flagsByKey, JavaMergedSymbolKind.CLASS, type.qualifiedName()));
        bytecode.methods().forEach(method -> markJvm(flagsByKey, JavaMergedSymbolKind.METHOD,
                method.ownerQualifiedName() + "#" + method.simpleName()));
        bytecode.fields().forEach(field -> markJvm(flagsByKey, JavaMergedSymbolKind.FIELD,
                field.ownerQualifiedName() + "#" + field.simpleName()));

        List<JavaMergedSymbol> symbols = flagsByKey.values().stream()
                .map(flags -> new JavaMergedSymbol(flags.kind, flags.stableKey, flags.sourcePresent, flags.jvmPresent))
                .sorted(Comparator.comparing((JavaMergedSymbol symbol) -> symbol.kind().name())
                        .thenComparing(JavaMergedSymbol::stableKey))
                .toList();
        return new JavaSymbolOriginMergeResult(symbols);
    }

    private static void markSource(Map<String, OriginFlags> flagsByKey, JavaMergedSymbolKind kind, String stableKey) {
        flagsByKey.computeIfAbsent(key(kind, stableKey), ignored -> new OriginFlags(kind, stableKey)).sourcePresent = true;
    }

    private static void markJvm(Map<String, OriginFlags> flagsByKey, JavaMergedSymbolKind kind, String stableKey) {
        flagsByKey.computeIfAbsent(key(kind, stableKey), ignored -> new OriginFlags(kind, stableKey)).jvmPresent = true;
    }

    private static String key(JavaMergedSymbolKind kind, String stableKey) {
        return kind.name() + ":" + stableKey;
    }

    private static final class OriginFlags {
        private final JavaMergedSymbolKind kind;
        private final String stableKey;
        private boolean sourcePresent;
        private boolean jvmPresent;

        OriginFlags(JavaMergedSymbolKind kind, String stableKey) {
            this.kind = kind;
            this.stableKey = stableKey;
        }
    }
}
