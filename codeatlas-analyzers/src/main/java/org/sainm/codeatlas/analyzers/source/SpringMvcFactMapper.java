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

public final class SpringMvcFactMapper {
    private static final String ANALYZER_ID = "spring-mvc";
    private static final String API_ENDPOINT_SOURCE_ROOT = "_api";

    private SpringMvcFactMapper() {
    }

    public static SpringMvcFactMapper defaults() {
        return new SpringMvcFactMapper();
    }

    public JavaSourceFactBatch map(
            SpringMvcAnalysisResult result,
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
        for (SpringRouteInfo route : result.routes()) {
            for (String httpMethod : route.httpMethods()) {
                for (String path : route.paths()) {
                    addRouteFact(facts, factKeys, evidenceByKey, context, route, httpMethod, path);
                }
            }
        }
        return new JavaSourceFactBatch(facts, List.copyOf(evidenceByKey.values()));
    }

    private static void addRouteFact(
            List<FactRecord> facts,
            Set<String> factKeys,
            Map<String, Evidence> evidenceByKey,
            JavaSourceFactContext context,
            SpringRouteInfo route,
            String httpMethod,
            String path) {
        Evidence evidence = Evidence.create(
                ANALYZER_ID,
                context.scopeKey(),
                route.location().relativePath(),
                "line:" + route.location().line(),
                1,
                SourceType.SPRING);
        FactRecord fact = FactRecord.create(
                List.of(context.sourceRootKey(), API_ENDPOINT_SOURCE_ROOT),
                apiEndpointId(context, httpMethod, path),
                methodId(context, route.ownerQualifiedName(), route.methodName(), route.methodSignature()),
                "ROUTES_TO",
                "spring-mvc",
                context.projectId(),
                context.snapshotId(),
                context.analysisRunId(),
                context.scopeRunId(),
                ANALYZER_ID,
                context.scopeKey(),
                evidence.evidenceKey(),
                Confidence.LIKELY,
                100,
                SourceType.SPRING);
        if (!factKeys.add(fact.factKey())) {
            return;
        }
        evidenceByKey.putIfAbsent(evidence.evidenceKey(), evidence);
        facts.add(fact);
    }

    private static String apiEndpointId(JavaSourceFactContext context, String httpMethod, String path) {
        return "api-endpoint://" + context.projectId() + "/" + context.moduleKey() + "/"
                + API_ENDPOINT_SOURCE_ROOT + "/" + httpMethod + ":" + normalizedEndpointPath(path);
    }

    private static String methodId(
            JavaSourceFactContext context,
            String ownerQualifiedName,
            String methodName,
            String signature) {
        return "method://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.sourceRootKey() + "/" + ownerQualifiedName + "#" + methodName + signature;
    }

    private static String normalizedEndpointPath(String path) {
        String normalized = path == null || path.isBlank() ? "/" : stripPathVariableRegex(path.trim());
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String stripPathVariableRegex(String path) {
        StringBuilder result = new StringBuilder();
        boolean insidePathVariable = false;
        boolean skippingRegex = false;
        int regexBraceDepth = 0;
        for (int index = 0; index < path.length(); index++) {
            char current = path.charAt(index);
            if (current == '{') {
                if (insidePathVariable && skippingRegex) {
                    regexBraceDepth++;
                } else {
                    insidePathVariable = true;
                    skippingRegex = false;
                    regexBraceDepth = 0;
                    result.append(current);
                }
                continue;
            }
            if (insidePathVariable && current == ':') {
                skippingRegex = true;
                continue;
            }
            if (insidePathVariable && current == '}') {
                if (skippingRegex && regexBraceDepth > 0) {
                    regexBraceDepth--;
                } else {
                    insidePathVariable = false;
                    skippingRegex = false;
                    regexBraceDepth = 0;
                    result.append(current);
                }
                continue;
            }
            if (!skippingRegex) {
                result.append(current);
            }
        }
        return result.toString();
    }
}
