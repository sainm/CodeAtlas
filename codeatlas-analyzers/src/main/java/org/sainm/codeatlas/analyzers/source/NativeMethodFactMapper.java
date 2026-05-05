package org.sainm.codeatlas.analyzers.source;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sainm.codeatlas.facts.Confidence;
import org.sainm.codeatlas.facts.Evidence;
import org.sainm.codeatlas.facts.FactRecord;
import org.sainm.codeatlas.facts.SourceType;

public final class NativeMethodFactMapper {
    private static final String ANALYZER_ID = "spoon-native";

    private NativeMethodFactMapper() {
    }

    public static NativeMethodFactMapper defaults() {
        return new NativeMethodFactMapper();
    }

    public JavaSourceFactBatch map(
            JavaSourceAnalysisResult result,
            JavaSourceFactContext context) {
        if (result == null) {
            throw new IllegalArgumentException("result is required");
        }
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        List<FactRecord> facts = new ArrayList<>();
        Set<String> factKeys = new HashSet<>();
        Map<String, Evidence> evidenceByKey = new LinkedHashMap<>();
        Confidence confidence = result.noClasspathFallbackUsed() ? Confidence.LIKELY : Confidence.CERTAIN;
        for (JavaMethodInfo method : result.methods()) {
            if (method.modifiers().contains("native")) {
                String methodId = methodId(context, method.ownerQualifiedName(), method.simpleName(), method.signature());
                String boundaryId = nativeBoundaryId(context, method.ownerQualifiedName(), method.simpleName(), method.signature());
                addFact(facts, factKeys, evidenceByKey, context,
                        methodId, boundaryId,
                        "HAS_NATIVE_BOUNDARY", "native-method", method.location(), confidence);
                String libraryId = nativeLibraryId(context, defaultLibraryName(method));
                addFact(facts, factKeys, evidenceByKey, context,
                        methodId, libraryId,
                        "CALLS_NATIVE", "native-method", method.location(), confidence);
            }
        }
        for (JavaInvocationInfo invocation : result.directInvocations()) {
            if (isSystemLoad(invocation)) {
                String sourceMethodId = ownerMethodId(result, context, invocation);
                if (sourceMethodId.isBlank()) {
                    continue;
                }
                String libraryName = libraryNameFromInvocation(invocation);
                String libraryId = nativeLibraryId(context, libraryName);
                addFact(facts, factKeys, evidenceByKey, context,
                        sourceMethodId, libraryId,
                        "CALLS_NATIVE", invocation.targetSimpleName(), invocation.location(), confidence);
            }
        }
        return new JavaSourceFactBatch(facts, List.copyOf(evidenceByKey.values()));
    }

    private static boolean isSystemLoad(JavaInvocationInfo invocation) {
        return invocation.targetQualifiedName().equals("java.lang.System")
                && (invocation.targetSimpleName().equals("load")
                        || invocation.targetSimpleName().equals("loadLibrary"));
    }

    private static String libraryNameFromInvocation(JavaInvocationInfo invocation) {
        return "System" + "_" + invocation.targetSimpleName();
    }

    private static String defaultLibraryName(JavaMethodInfo method) {
        int dot = method.ownerQualifiedName().lastIndexOf('.');
        return dot >= 0 ? method.ownerQualifiedName().substring(dot + 1) : method.ownerQualifiedName();
    }

    private static String ownerMethodId(
            JavaSourceAnalysisResult result,
            JavaSourceFactContext context,
            JavaInvocationInfo invocation) {
        return result.methods().stream()
                .filter(method -> method.ownerQualifiedName().equals(invocation.ownerQualifiedName())
                        && method.simpleName().equals(invocation.ownerMethodName())
                        && (invocation.ownerMethodSignature().isBlank()
                                || method.signature().equals(invocation.ownerMethodSignature())))
                .findFirst()
                .map(method -> methodId(context, method.ownerQualifiedName(), method.simpleName(), method.signature()))
                .orElse("");
    }

    private static void addFact(
            List<FactRecord> facts,
            Set<String> factKeys,
            Map<String, Evidence> evidenceByKey,
            JavaSourceFactContext context,
            String sourceIdentityId,
            String targetIdentityId,
            String relationName,
            String qualifier,
            SourceLocation location,
            Confidence confidence) {
        Evidence evidence = Evidence.create(
                ANALYZER_ID,
                context.scopeKey(),
                location.relativePath(),
                "line:" + location.line(),
                1,
                SourceType.SPOON);
        FactRecord fact = FactRecord.create(
                List.of(context.sourceRootKey()),
                sourceIdentityId,
                targetIdentityId,
                relationName,
                qualifier,
                context.projectId(),
                context.snapshotId(),
                context.analysisRunId(),
                context.scopeRunId(),
                ANALYZER_ID,
                context.scopeKey(),
                evidence.evidenceKey(),
                confidence,
                100,
                SourceType.SPOON);
        if (!factKeys.add(fact.factKey())) {
            return;
        }
        evidenceByKey.putIfAbsent(evidence.evidenceKey(), evidence);
        facts.add(fact);
    }

    private static String methodId(
            JavaSourceFactContext context,
            String ownerQualifiedName,
            String methodName,
            String signature) {
        return "method://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.sourceRootKey() + "/" + ownerQualifiedName + "#" + methodName + signature;
    }

    private static String nativeBoundaryId(
            JavaSourceFactContext context,
            String ownerQualifiedName,
            String methodName,
            String signature) {
        return "boundary-symbol://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.sourceRootKey() + "/" + ownerQualifiedName + "#" + methodName + signature + "@NATIVE";
    }

    private static String nativeLibraryId(JavaSourceFactContext context, String libraryName) {
        return "native-library://" + context.projectId() + "/" + context.moduleKey() + "/"
                + "native" + "/" + skipQualifiedPrefix(libraryName);
    }

    private static String skipQualifiedPrefix(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }
}
