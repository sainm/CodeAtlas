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

public final class SeasarDiconFactMapper {
    private static final String ANALYZER_ID = "seasar2-dicon";

    private SeasarDiconFactMapper() {
    }

    public static SeasarDiconFactMapper defaults() {
        return new SeasarDiconFactMapper();
    }

    public JavaSourceFactBatch map(SeasarDiconAnalysisResult result, SeasarDiconFactContext context) {
        if (result == null) {
            throw new IllegalArgumentException("result is required");
        }
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        List<FactRecord> facts = new ArrayList<>();
        Set<String> factKeys = new HashSet<>();
        Map<String, Evidence> evidenceByKey = new LinkedHashMap<>();
        for (SeasarDiconComponentInfo component : result.components()) {
            addFact(facts, factKeys, evidenceByKey, context, component.location(),
                    sourceFileId(context, component.diconPath()),
                    componentId(context, component),
                    "DECLARES",
                    "seasar2-dicon-component");
            addComponentBindingFacts(facts, factKeys, evidenceByKey, context, component);
        }
        for (SeasarDiconIncludeInfo include : result.includes()) {
            addFact(facts, factKeys, evidenceByKey, context, include.location(),
                    sourceFileId(context, include.diconPath()),
                    sourceFileId(context, resolveAgainstDicon(include.diconPath(), include.path())),
                    "INCLUDES",
                    "seasar2-dicon-include");
        }
        for (SeasarDiconPropertyInfo property : result.properties()) {
            if (property.componentName().isBlank() || property.name().isBlank()) {
                continue;
            }
            addFact(facts, factKeys, evidenceByKey, context, property.location(),
                    componentId(context, property.diconPath(), property.componentName()),
                    propertyId(context, property),
                    "CONFIGURES_PROPERTY",
                    "seasar2-dicon-property");
        }
        for (SeasarDiconAspectInfo aspect : result.aspects()) {
            if (aspect.componentName().isBlank() || aspect.interceptor().isBlank()) {
                continue;
            }
            addFact(facts, factKeys, evidenceByKey, context, aspect.location(),
                    componentId(context, aspect.diconPath(), aspect.componentName()),
                    componentId(context, aspect.diconPath(), aspect.interceptor()),
                    "INTERCEPTS",
                    "seasar2-dicon-aspect");
        }
        return new JavaSourceFactBatch(facts, List.copyOf(evidenceByKey.values()));
    }

    private static void addComponentBindingFacts(
            List<FactRecord> facts,
            Set<String> factKeys,
            Map<String, Evidence> evidenceByKey,
            SeasarDiconFactContext context,
            SeasarDiconComponentInfo component) {
        String sourceId = componentId(context, component);
        if (!component.className().isBlank()) {
            addFact(facts, factKeys, evidenceByKey, context, component.location(),
                    sourceId,
                    classId(context, component.className()),
                    "AUTO_BINDS_TO",
                    "seasar2-dicon-class");
        }
        if (!component.interfaceName().isBlank()) {
            addFact(facts, factKeys, evidenceByKey, context, component.location(),
                    sourceId,
                    classId(context, component.interfaceName()),
                    "AUTO_BINDS_TO",
                    "seasar2-dicon-interface");
        }
    }

    private static void addFact(
            List<FactRecord> facts,
            Set<String> factKeys,
            Map<String, Evidence> evidenceByKey,
            SeasarDiconFactContext context,
            SourceLocation location,
            String sourceIdentityId,
            String targetIdentityId,
            String relationName,
            String qualifier) {
        Evidence evidence = Evidence.create(
                ANALYZER_ID,
                context.scopeKey(),
                location.relativePath(),
                "line:" + location.line(),
                1,
                SourceType.XML);
        FactRecord fact = FactRecord.create(
                context.identitySourceRoots(),
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
                Confidence.POSSIBLE,
                100,
                SourceType.XML);
        if (!factKeys.add(fact.factKey())) {
            return;
        }
        evidenceByKey.putIfAbsent(evidence.evidenceKey(), evidence);
        facts.add(fact);
    }

    private static String sourceFileId(SeasarDiconFactContext context, String diconPath) {
        return "source-file://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.configSourceRootKey() + "/" + sourceRootRelative(context.configSourceRootKey(), diconPath);
    }

    private static String componentId(SeasarDiconFactContext context, SeasarDiconComponentInfo component) {
        return componentId(context, component.diconPath(), componentKey(component));
    }

    private static String componentId(SeasarDiconFactContext context, String diconPath, String componentName) {
        return "dicon-component://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.configSourceRootKey() + "/" + sourceRootRelative(context.configSourceRootKey(), diconPath)
                + "#component[" + safeFragment(componentName) + "]";
    }

    private static String componentKey(SeasarDiconComponentInfo component) {
        if (!component.name().isBlank()) {
            return component.name();
        }
        if (!component.className().isBlank()) {
            return component.className();
        }
        if (!component.interfaceName().isBlank()) {
            return component.interfaceName();
        }
        return "_anonymous";
    }

    private static String propertyId(SeasarDiconFactContext context, SeasarDiconPropertyInfo property) {
        return "config-key://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.configSourceRootKey() + "/" + sourceRootRelative(context.configSourceRootKey(), property.diconPath())
                + "#property[" + safeFragment(property.componentName()) + ":" + safeFragment(property.name()) + "]";
    }

    private static String classId(SeasarDiconFactContext context, String className) {
        return "class://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.javaSourceRootKey() + "/" + className;
    }

    private static String sourceRootRelative(String sourceRootKey, String path) {
        String normalized = normalizeLocalPath(path);
        String prefix = sourceRootKey.endsWith("/") ? sourceRootKey : sourceRootKey + "/";
        return normalized.startsWith(prefix) ? normalized.substring(prefix.length()) : normalized;
    }

    private static String resolveAgainstDicon(String diconPath, String target) {
        String normalizedTarget = target == null ? "" : target.trim().replace('\\', '/');
        if (normalizedTarget.startsWith("/")) {
            return normalizeLocalPath(normalizedTarget);
        }
        String base = diconPath == null ? "" : diconPath.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        String directory = slash < 0 ? "" : base.substring(0, slash);
        return normalizeLocalPath(directory.isBlank() ? normalizedTarget : directory + "/" + normalizedTarget);
    }

    private static String normalizeLocalPath(String path) {
        List<String> segments = new ArrayList<>();
        for (String segment : (path == null ? "" : path).replace('\\', '/').split("/")) {
            if (segment.isBlank() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                if (!segments.isEmpty()) {
                    segments.remove(segments.size() - 1);
                }
                continue;
            }
            segments.add(segment);
        }
        return String.join("/", segments);
    }

    private static String safeFragment(String value) {
        String source = value == null ? "" : value.trim();
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '#' || current == '?' || Character.isWhitespace(current) || Character.isISOControl(current)) {
                builder.append('-');
            } else {
                builder.append(current);
            }
        }
        return builder.isEmpty() ? "_anonymous" : builder.toString();
    }
}
