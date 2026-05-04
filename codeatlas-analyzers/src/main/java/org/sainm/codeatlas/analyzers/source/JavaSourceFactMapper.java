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
        Set<String> factKeys = new HashSet<>();
        Map<String, Evidence> evidenceByKey = new LinkedHashMap<>();
        Confidence confidence = result.noClasspathFallbackUsed() ? Confidence.LIKELY : Confidence.CERTAIN;
        for (JavaClassInfo classInfo : result.classes()) {
            addFact(facts, factKeys, evidenceByKey, context, sourceFileId(context, classInfo.location().relativePath()),
                    classId(context, classInfo.qualifiedName()), "DECLARES", "class", classInfo.location(), confidence);
        }
        for (JavaMethodInfo method : result.methods()) {
            addFact(facts, factKeys, evidenceByKey, context, classId(context, method.ownerQualifiedName()),
                    methodId(context, method.ownerQualifiedName(), method.simpleName(), method.signature()),
                    "DECLARES", "method", method.location(), confidence);
            addMethodEntryPointFacts(facts, factKeys, evidenceByKey, context, method, confidence);
        }
        for (JavaFieldInfo field : result.fields()) {
            addFact(facts, factKeys, evidenceByKey, context, classId(context, field.ownerQualifiedName()),
                    fieldId(context, field.ownerQualifiedName(), field.simpleName(), field.typeDescriptor()),
                    "DECLARES", "field", field.location(), confidence);
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
            addFact(facts, factKeys, evidenceByKey, context, sourceMethodId,
                    methodId(context, invocation.targetQualifiedName(), invocation.targetSimpleName(), invocation.targetSignature()),
                    "CALLS", "direct", invocation.location(), confidence);
        }
        return new JavaSourceFactBatch(facts, List.copyOf(evidenceByKey.values()));
    }

    private static void addMethodEntryPointFacts(
            List<FactRecord> facts,
            Set<String> factKeys,
            Map<String, Evidence> evidenceByKey,
            JavaSourceFactContext context,
            JavaMethodInfo method,
            Confidence confidence) {
        String methodId = methodId(context, method.ownerQualifiedName(), method.simpleName(), method.signature());
        if (isMainMethod(method)) {
            addEntryPointFact(facts, factKeys, evidenceByKey, context, methodId,
                    EntryPointIds.main(context, method.ownerQualifiedName()), "java-main", method.location(), confidence);
        }
        if (hasAnnotation(method, "Scheduled")) {
            addEntryPointFact(facts, factKeys, evidenceByKey, context, methodId,
                    EntryPointIds.scheduler(context, method.ownerQualifiedName(), method.simpleName()),
                    "spring-scheduled", method.location(), confidence);
        }
        if (hasAnyAnnotation(method, List.of("JmsListener", "KafkaListener", "RabbitListener", "MessageMapping"))) {
            addEntryPointFact(facts, factKeys, evidenceByKey, context, methodId,
                    EntryPointIds.messageListener(
                            context, method.ownerQualifiedName(), method.simpleName(), method.signature()),
                    "message-listener", method.location(), confidence);
        }
    }

    private static void addEntryPointFact(
            List<FactRecord> facts,
            Set<String> factKeys,
            Map<String, Evidence> evidenceByKey,
            JavaSourceFactContext context,
            String sourceIdentityId,
            String targetIdentityId,
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
                EntryPointIds.withEntryPointRoot(List.of(context.sourceRootKey())),
                sourceIdentityId,
                targetIdentityId,
                "DECLARES_ENTRYPOINT",
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

    private static boolean isMainMethod(JavaMethodInfo method) {
        return method.simpleName().equals("main")
                && method.returnTypeName().equals("void")
                && method.signature().equals("([Ljava/lang/String;)V")
                && method.modifiers().contains("public")
                && method.modifiers().contains("static");
    }

    private static boolean hasAnyAnnotation(JavaMethodInfo method, List<String> simpleNames) {
        return simpleNames.stream().anyMatch(simpleName -> hasAnnotation(method, simpleName));
    }

    private static boolean hasAnnotation(JavaMethodInfo method, String simpleName) {
        return method.annotations().stream()
                .anyMatch(annotation -> annotation.equals(simpleName) || annotation.endsWith("." + simpleName));
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

    private static String fieldId(
            JavaSourceFactContext context,
            String ownerQualifiedName,
            String fieldName,
            String typeDescriptor) {
        return "field://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.sourceRootKey() + "/" + ownerQualifiedName + "#" + fieldName + ":" + typeDescriptor;
    }

    private static String stripSourceRoot(String sourceRootKey, String relativePath) {
        String prefix = sourceRootKey.endsWith("/") ? sourceRootKey : sourceRootKey + "/";
        return relativePath.startsWith(prefix) ? relativePath.substring(prefix.length()) : relativePath;
    }
}
