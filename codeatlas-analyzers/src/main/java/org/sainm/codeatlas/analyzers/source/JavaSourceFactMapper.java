package org.sainm.codeatlas.analyzers.source;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sainm.codeatlas.facts.Confidence;
import org.sainm.codeatlas.facts.Evidence;
import org.sainm.codeatlas.facts.FactRecord;
import org.sainm.codeatlas.facts.SourceType;

public final class JavaSourceFactMapper {
    private static final String ANALYZER_ID = "spoon";

    private JavaSourceFactMapper() {
    }

    public static JavaSourceFactMapper defaults() {
        return new JavaSourceFactMapper();
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
        Map<String, Evidence> evidenceByKey = new LinkedHashMap<>();
        for (JavaClassInfo classInfo : result.classes()) {
            addFact(facts, evidenceByKey, context, sourceFileId(context, classInfo.location().relativePath()),
                    classId(context, classInfo.qualifiedName()), "DECLARES", "class", classInfo.location());
        }
        for (JavaMethodInfo method : result.methods()) {
            addFact(facts, evidenceByKey, context, classId(context, method.ownerQualifiedName()),
                    methodId(context, method.ownerQualifiedName(), method.simpleName(), method.signature()),
                    "DECLARES", "method", method.location());
        }
        for (JavaFieldInfo field : result.fields()) {
            addFact(facts, evidenceByKey, context, classId(context, field.ownerQualifiedName()),
                    fieldId(context, field.ownerQualifiedName(), field.simpleName()), "DECLARES", "field", field.location());
        }
        for (JavaInvocationInfo invocation : result.directInvocations()) {
            if (invocation.ownerQualifiedName().isBlank()
                    || invocation.ownerMethodName().isBlank()
                    || invocation.targetQualifiedName().isBlank()
                    || invocation.targetSignature().isBlank()) {
                continue;
            }
            String sourceMethodId = ownerMethodId(result, context, invocation);
            if (sourceMethodId.isBlank()) {
                continue;
            }
            addFact(facts, evidenceByKey, context, sourceMethodId,
                    methodId(context, invocation.targetQualifiedName(), invocation.targetSimpleName(), invocation.targetSignature()),
                    "CALLS", "direct", invocation.location());
        }
        return new JavaSourceFactBatch(facts, List.copyOf(evidenceByKey.values()));
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
            Map<String, Evidence> evidenceByKey,
            JavaSourceFactContext context,
            String sourceIdentityId,
            String targetIdentityId,
            String relationName,
            String qualifier,
            SourceLocation location) {
        Evidence evidence = Evidence.create(
                ANALYZER_ID,
                context.scopeKey(),
                location.relativePath(),
                "line:" + location.line(),
                1,
                SourceType.SPOON);
        evidenceByKey.putIfAbsent(evidence.evidenceKey(), evidence);
        facts.add(FactRecord.create(
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
                Confidence.CERTAIN,
                100,
                SourceType.SPOON));
    }

    private static String sourceFileId(JavaSourceFactContext context, String relativePath) {
        String ownerPath = stripSourceRoot(context.sourceRootKey(), relativePath);
        return "source-file://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.sourceRootKey() + "/" + ownerPath;
    }

    private static String classId(JavaSourceFactContext context, String qualifiedName) {
        return "class://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.sourceRootKey() + "/" + qualifiedName;
    }

    private static String methodId(
            JavaSourceFactContext context,
            String ownerQualifiedName,
            String methodName,
            String signature) {
        return "method://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.sourceRootKey() + "/" + ownerQualifiedName + "#" + methodName + signature;
    }

    private static String fieldId(JavaSourceFactContext context, String ownerQualifiedName, String fieldName) {
        return "field://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.sourceRootKey() + "/" + ownerQualifiedName + "#" + fieldName;
    }

    private static String stripSourceRoot(String sourceRootKey, String relativePath) {
        String prefix = sourceRootKey.endsWith("/") ? sourceRootKey : sourceRootKey + "/";
        return relativePath.startsWith(prefix) ? relativePath.substring(prefix.length()) : relativePath;
    }
}
