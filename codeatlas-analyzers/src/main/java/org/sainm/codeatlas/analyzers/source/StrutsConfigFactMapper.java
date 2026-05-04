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

public final class StrutsConfigFactMapper {
    private static final String ANALYZER_ID = "struts-config";
    private static final String ACTION_PATH_SOURCE_ROOT = "_actions";
    private static final String ACTION_EXECUTE_DESCRIPTOR =
            "(Lorg/apache/struts/action/ActionMapping;Lorg/apache/struts/action/ActionForm;"
                    + "Ljavax/servlet/http/HttpServletRequest;Ljavax/servlet/http/HttpServletResponse;)"
                    + "Lorg/apache/struts/action/ActionForward;";

    private StrutsConfigFactMapper() {
    }

    public static StrutsConfigFactMapper defaults() {
        return new StrutsConfigFactMapper();
    }

    public JavaSourceFactBatch map(
            StrutsConfigAnalysisResult result,
            StrutsConfigFactContext context) {
        if (result == null) {
            throw new IllegalArgumentException("result is required");
        }
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        List<FactRecord> facts = new ArrayList<>();
        Set<String> factKeys = new HashSet<>();
        Map<String, Evidence> evidenceByKey = new LinkedHashMap<>();
        for (StrutsActionInfo action : result.actions()) {
            if (!action.type().isBlank()) {
                addActionRouteFact(facts, factKeys, evidenceByKey, context, action);
            }
            addActionForwardFacts(facts, factKeys, evidenceByKey, context, action);
        }
        return new JavaSourceFactBatch(facts, List.copyOf(evidenceByKey.values()));
    }

    private static void addActionRouteFact(
            List<FactRecord> facts,
            Set<String> factKeys,
            Map<String, Evidence> evidenceByKey,
            StrutsConfigFactContext context,
            StrutsActionInfo action) {
        Evidence evidence = Evidence.create(
                ANALYZER_ID,
                context.scopeKey(),
                action.location().relativePath(),
                "line:" + action.location().line(),
                1,
                SourceType.STRUTS);
        Confidence confidence = actionRouteConfidence(action);
        FactRecord fact = FactRecord.create(
                context.identitySourceRoots(),
                actionPathId(context, action.path()),
                methodId(context, action.type(), actionMethodName(action), ACTION_EXECUTE_DESCRIPTOR),
                "ROUTES_TO",
                qualifier(action),
                context.projectId(),
                context.snapshotId(),
                context.analysisRunId(),
                context.scopeRunId(),
                ANALYZER_ID,
                context.scopeKey(),
                evidence.evidenceKey(),
                confidence,
                100,
                SourceType.STRUTS);
        if (!factKeys.add(fact.factKey())) {
            return;
        }
        evidenceByKey.putIfAbsent(evidence.evidenceKey(), evidence);
        facts.add(fact);
    }

    private static void addActionForwardFacts(
            List<FactRecord> facts,
            Set<String> factKeys,
            Map<String, Evidence> evidenceByKey,
            StrutsConfigFactContext context,
            StrutsActionInfo action) {
        for (StrutsForwardInfo forward : action.forwards()) {
            String targetId = forwardTargetId(context, forward);
            if (targetId.isBlank()) {
                continue;
            }
            Evidence evidence = Evidence.create(
                    ANALYZER_ID,
                    context.scopeKey(),
                    forward.location().relativePath(),
                    "line:" + forward.location().line(),
                    1,
                    SourceType.STRUTS);
            FactRecord fact = FactRecord.create(
                    context.identitySourceRoots(),
                    forwardSourceId(context, action),
                    targetId,
                    "FORWARDS_TO",
                    "struts-forward:" + forward.name(),
                    context.projectId(),
                    context.snapshotId(),
                    context.analysisRunId(),
                    context.scopeRunId(),
                    ANALYZER_ID,
                    context.scopeKey(),
                    evidence.evidenceKey(),
                    Confidence.LIKELY,
                    100,
                    SourceType.STRUTS);
            if (!factKeys.add(fact.factKey())) {
                continue;
            }
            evidenceByKey.putIfAbsent(evidence.evidenceKey(), evidence);
            facts.add(fact);
        }
    }

    private static String forwardSourceId(StrutsConfigFactContext context, StrutsActionInfo action) {
        if (action.type().isBlank()) {
            return actionPathId(context, action.path());
        }
        return methodId(context, action.type(), actionMethodName(action), ACTION_EXECUTE_DESCRIPTOR);
    }

    private static Confidence actionRouteConfidence(StrutsActionInfo action) {
        if (action.dispatchKind() == StrutsActionDispatchKind.STANDARD
                || isConfiguredMappingDispatchAction(action)) {
            return Confidence.LIKELY;
        }
        return Confidence.POSSIBLE;
    }

    private static String actionMethodName(StrutsActionInfo action) {
        if (isConfiguredMappingDispatchAction(action)) {
            return action.parameter();
        }
        return "execute";
    }

    private static boolean isConfiguredMappingDispatchAction(StrutsActionInfo action) {
        return action.dispatchKind() == StrutsActionDispatchKind.MAPPING_DISPATCH
                && !action.parameter().isBlank();
    }

    private static String qualifier(StrutsActionInfo action) {
        if (action.dispatchKind() == StrutsActionDispatchKind.STANDARD) {
            return "struts-action";
        }
        return "struts-" + action.dispatchKind().name().toLowerCase().replace('_', '-') + "-boundary";
    }

    private static String actionPathId(StrutsConfigFactContext context, String actionPath) {
        return "action-path://" + context.projectId() + "/" + context.moduleKey() + "/"
                + ACTION_PATH_SOURCE_ROOT + "/" + normalizedActionPath(actionPath);
    }

    private static String normalizedActionPath(String actionPath) {
        String normalized = normalizeLocalPath(stripQueryAndFragment(actionPath));
        if (normalized.endsWith(".do")) {
            normalized = normalized.substring(0, normalized.length() - ".do".length());
        }
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? "_root" : normalized;
    }

    private static String methodId(
            StrutsConfigFactContext context,
            String actionClass,
            String methodName,
            String descriptor) {
        return "method://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.javaSourceRootKey() + "/" + actionClass + "#" + methodName + descriptor;
    }

    private static String jspPageId(StrutsConfigFactContext context, String path) {
        return "jsp-page://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.webSourceRootKey() + "/" + normalizedJspPath(context.webSourceRootKey(), path);
    }

    private static String htmlPageId(StrutsConfigFactContext context, String path) {
        return "html-page://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.webSourceRootKey() + "/" + normalizedJspPath(context.webSourceRootKey(), path);
    }

    private static String forwardTargetId(StrutsConfigFactContext context, StrutsForwardInfo forward) {
        String path = moduleRelativeTargetPath(forward);
        if (isExternalUrl(path)) {
            return "";
        }
        if (isJspPath(path)) {
            return jspPageId(context, path);
        }
        if (isHtmlPath(path)) {
            return htmlPageId(context, path);
        }
        if (isStrutsActionPath(path)) {
            return actionPathId(context, path);
        }
        return "";
    }

    private static String moduleRelativeTargetPath(StrutsForwardInfo forward) {
        String path = forward.path();
        if (forward.contextRelative() || forward.moduleKey().isBlank()
                || isExternalUrl(path) || stripQueryAndFragment(path).isBlank()) {
            return path;
        }
        String normalized = stripQueryAndFragment(path).replace('\\', '/');
        String modulePrefix = "/" + forward.moduleKey();
        if (normalized.equals(modulePrefix)
                || normalized.startsWith(modulePrefix + "/")
                || normalized.equals(forward.moduleKey())
                || normalized.startsWith(forward.moduleKey() + "/")) {
            return path;
        }
        String separator = path.startsWith("/") ? "" : "/";
        return modulePrefix + separator + path;
    }

    private static String normalizedJspPath(String sourceRootKey, String path) {
        String normalized = stripQueryAndFragment(path).replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        String prefix = sourceRootKey.endsWith("/") ? sourceRootKey : sourceRootKey + "/";
        if (normalized.startsWith(prefix)) {
            normalized = normalized.substring(prefix.length());
        }
        return normalizeLocalPath(normalized);
    }

    private static String normalizeLocalPath(String path) {
        List<String> segments = new ArrayList<>();
        for (String segment : path.replace('\\', '/').split("/")) {
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

    private static boolean isJspPath(String path) {
        String normalized = stripQueryAndFragment(path).toLowerCase();
        return normalized.endsWith(".jsp") || normalized.endsWith(".jspx");
    }

    private static boolean isHtmlPath(String path) {
        String normalized = stripQueryAndFragment(path).toLowerCase();
        return normalized.endsWith(".html") || normalized.endsWith(".htm");
    }

    private static boolean isStrutsActionPath(String path) {
        String normalized = stripQueryAndFragment(path).toLowerCase();
        return normalized.endsWith(".do");
    }

    private static boolean isExternalUrl(String path) {
        String normalized = stripQueryAndFragment(path);
        if (normalized.startsWith("//")) {
            return true;
        }
        int schemeSeparator = normalized.indexOf("://");
        if (schemeSeparator <= 0) {
            return false;
        }
        for (int index = 0; index < schemeSeparator; index++) {
            char current = normalized.charAt(index);
            boolean valid = Character.isLetterOrDigit(current) || current == '+' || current == '-' || current == '.';
            if (!valid) {
                return false;
            }
        }
        return true;
    }

    private static String stripQueryAndFragment(String path) {
        String normalized = path == null ? "" : path.trim();
        int queryStart = normalized.indexOf('?');
        int fragmentStart = normalized.indexOf('#');
        int cut = -1;
        if (queryStart >= 0 && fragmentStart >= 0) {
            cut = Math.min(queryStart, fragmentStart);
        } else if (queryStart >= 0) {
            cut = queryStart;
        } else if (fragmentStart >= 0) {
            cut = fragmentStart;
        }
        return cut >= 0 ? normalized.substring(0, cut) : normalized;
    }
}
