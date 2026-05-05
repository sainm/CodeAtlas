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

public final class VariableTraceFactMapper {
    private static final String ANALYZER_ID = "variable-trace";

    private VariableTraceFactMapper() {
    }

    public static VariableTraceFactMapper defaults() {
        return new VariableTraceFactMapper();
    }

    public JavaSourceFactBatch map(VariableTraceAnalysisResult result, VariableTraceFactContext context) {
        if (result == null) {
            throw new IllegalArgumentException("result is required");
        }
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        List<FactRecord> facts = new ArrayList<>();
        Set<String> factKeys = new HashSet<>();
        Map<String, Evidence> evidenceByKey = new LinkedHashMap<>();
        for (RequestDerivedArgumentInfo argument : result.requestDerivedArguments()) {
            addFact(facts, factKeys, evidenceByKey, context,
                    requestParamId(context, argument.requestParameterName()),
                    paramSlotId(context, argument.targetQualifiedName(), argument.targetMethodName(), argument.targetSignature(),
                            argument.argumentIndex(), argument.argumentDescriptor()),
                    "request-derived-argument",
                    argument.location());
        }
        for (ParameterDerivedArgumentInfo argument : result.parameterDerivedArguments()) {
            addFact(facts, factKeys, evidenceByKey, context,
                    paramSlotId(context, argument.ownerQualifiedName(), argument.methodName(), argument.methodSignature(),
                            argument.sourceParameterIndex(), argument.sourceParameterDescriptor()),
                    paramSlotId(context, argument.targetQualifiedName(), argument.targetMethodName(), argument.targetSignature(),
                            argument.targetArgumentIndex(), argument.targetArgumentDescriptor()),
                    "parameter-derived-argument",
                    argument.location());
        }
        return new JavaSourceFactBatch(facts, List.copyOf(evidenceByKey.values()));
    }

    private static void addFact(
            List<FactRecord> facts,
            Set<String> factKeys,
            Map<String, Evidence> evidenceByKey,
            VariableTraceFactContext context,
            String sourceIdentityId,
            String targetIdentityId,
            String qualifier,
            SourceLocation location) {
        Evidence evidence = Evidence.create(
                ANALYZER_ID,
                context.scopeKey(),
                location.relativePath(),
                "line:" + location.line(),
                1,
                SourceType.IMPACT_FLOW);
        FactRecord fact = FactRecord.create(
                List.of(context.javaSourceRootKey(), context.requestScopeKey()),
                sourceIdentityId,
                targetIdentityId,
                "PASSES_PARAM",
                qualifier,
                context.projectId(),
                context.snapshotId(),
                context.analysisRunId(),
                context.scopeRunId(),
                ANALYZER_ID,
                context.scopeKey(),
                evidence.evidenceKey(),
                Confidence.LIKELY,
                90,
                SourceType.IMPACT_FLOW);
        if (!factKeys.add(fact.factKey())) {
            return;
        }
        evidenceByKey.putIfAbsent(evidence.evidenceKey(), evidence);
        facts.add(fact);
    }

    private static String requestParamId(VariableTraceFactContext context, String name) {
        return "request-param://" + context.projectId() + "/" + context.moduleKey()
                + "/" + context.requestScopeKey() + "#" + name;
    }

    private static String paramSlotId(
            VariableTraceFactContext context,
            String qualifiedName,
            String methodName,
            String signature,
            int argumentIndex,
            String argumentDescriptor) {
        return "param-slot://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.javaSourceRootKey() + "/" + qualifiedName + "#"
                + methodName + signature + ":param["
                + argumentIndex + ":" + argumentDescriptor + "]";
    }
}
