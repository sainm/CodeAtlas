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

public final class JspFactMapper {
    private static final String ANALYZER_ID = "jsp-token";
    private static final String ACTION_PATH_SOURCE_ROOT = "_actions";
    private static final String API_ENDPOINT_SOURCE_ROOT = "_api";

    private JspFactMapper() {
    }

    public static JspFactMapper defaults() {
        return new JspFactMapper();
    }

    public JavaSourceFactBatch map(
            JspAnalysisResult result,
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
        for (JspPageInfo page : result.pages()) {
            addPageEntryPointFact(facts, factKeys, evidenceByKey, context, page.location());
        }
        for (JspFormInfo form : result.forms()) {
            addPageEntryPointFact(facts, factKeys, evidenceByKey, context, form.location());
            addContainsFact(facts, factKeys, evidenceByKey, context, form.location(),
                    jspPageId(context, form.location().relativePath()), jspFormId(context, form));
            addFormSubmitFact(facts, factKeys, evidenceByKey, context, form);
        }
        Map<String, JspFormInfo> formsByKey = formsByKey(result.forms());
        for (JspInputInfo input : result.inputs()) {
            JspFormInfo ownerForm = ownerForm(input.formKey(), result.forms(), formsByKey);
            if (ownerForm != null) {
                addPageEntryPointFact(facts, factKeys, evidenceByKey, context, input.location());
                addContainsFact(facts, factKeys, evidenceByKey, context, input.location(),
                        jspPageId(context, input.location().relativePath()), jspInputId(context, ownerForm, input));
                addInputBindingFact(facts, factKeys, evidenceByKey, context, ownerForm, input);
            }
        }
        for (JspRequestParameterAccessInfo access : result.requestParameters()) {
            addPageEntryPointFact(facts, factKeys, evidenceByKey, context, access.location());
            addRequestParameterAccessFact(facts, factKeys, evidenceByKey, context, access);
        }
        for (JspForwardInfo forward : result.forwards()) {
            addPageEntryPointFact(facts, factKeys, evidenceByKey, context, forward.location());
            addForwardFact(facts, factKeys, evidenceByKey, context, forward);
        }
        for (JspIncludeInfo include : result.includes()) {
            addPageEntryPointFact(facts, factKeys, evidenceByKey, context, include.location());
            addIncludeFact(facts, factKeys, evidenceByKey, context, include);
        }
        return new JavaSourceFactBatch(facts, List.copyOf(evidenceByKey.values()));
    }

    private static void addPageEntryPointFact(
            List<FactRecord> facts,
            Set<String> factKeys,
            Map<String, Evidence> evidenceByKey,
            JavaSourceFactContext context,
            SourceLocation location) {
        Evidence evidence = evidence(context, location);
        FactRecord fact = FactRecord.create(
                identitySourceRoots(context),
                jspPageId(context, location.relativePath()),
                EntryPointIds.jsp(context, localSourcePath(context, location.relativePath())),
                "DECLARES_ENTRYPOINT",
                "jsp-page",
                context.projectId(),
                context.snapshotId(),
                context.analysisRunId(),
                context.scopeRunId(),
                ANALYZER_ID,
                context.scopeKey(),
                evidence.evidenceKey(),
                Confidence.LIKELY,
                100,
                SourceType.JSP_TOKEN);
        addFact(facts, factKeys, evidenceByKey, evidence, fact);
    }

    private static void addContainsFact(
            List<FactRecord> facts,
            Set<String> factKeys,
            Map<String, Evidence> evidenceByKey,
            JavaSourceFactContext context,
            SourceLocation location,
            String sourceIdentityId,
            String targetIdentityId) {
        Evidence evidence = evidence(context, location);
        FactRecord fact = FactRecord.create(
                identitySourceRoots(context),
                sourceIdentityId,
                targetIdentityId,
                "CONTAINS",
                "jsp-structure",
                context.projectId(),
                context.snapshotId(),
                context.analysisRunId(),
                context.scopeRunId(),
                ANALYZER_ID,
                context.scopeKey(),
                evidence.evidenceKey(),
                Confidence.CERTAIN,
                100,
                SourceType.JSP_TOKEN);
        addFact(facts, factKeys, evidenceByKey, evidence, fact);
    }

    private static void addFormSubmitFact(
            List<FactRecord> facts,
            Set<String> factKeys,
            Map<String, Evidence> evidenceByKey,
            JavaSourceFactContext context,
            JspFormInfo form) {
        if (isDynamicTarget(form.action())
                || (!isCurrentPageTarget(form.action()) && isNonRoutableTarget(form.action()))) {
            return;
        }
        Evidence evidence = evidence(context, form.location());
        FactRecord fact = FactRecord.create(
                identitySourceRoots(context),
                jspFormId(context, form),
                routeTargetId(context, form.location().relativePath(), form.action(), form.method(), form.sourceTag()),
                "SUBMITS_TO",
                "jsp-form",
                context.projectId(),
                context.snapshotId(),
                context.analysisRunId(),
                context.scopeRunId(),
                ANALYZER_ID,
                context.scopeKey(),
                evidence.evidenceKey(),
                Confidence.LIKELY,
                100,
                SourceType.JSP_TOKEN);
        addFact(facts, factKeys, evidenceByKey, evidence, fact);
    }

    private static void addRequestParameterAccessFact(
            List<FactRecord> facts,
            Set<String> factKeys,
            Map<String, Evidence> evidenceByKey,
            JavaSourceFactContext context,
            JspRequestParameterAccessInfo access) {
        if (access.accessKind() != JspRequestParameterAccessKind.READ) {
            return;
        }
        Evidence evidence = evidence(context, access.location());
        FactRecord fact = FactRecord.create(
                identitySourceRoots(context),
                jspPageId(context, access.location().relativePath()),
                requestParameterId(context, access.name()),
                "READS_REQUEST_PARAM",
                "jsp-request-param",
                context.projectId(),
                context.snapshotId(),
                context.analysisRunId(),
                context.scopeRunId(),
                ANALYZER_ID,
                context.scopeKey(),
                evidence.evidenceKey(),
                Confidence.LIKELY,
                100,
                SourceType.JSP_TOKEN);
        addFact(facts, factKeys, evidenceByKey, evidence, fact);
    }

    private static void addInputBindingFact(
            List<FactRecord> facts,
            Set<String> factKeys,
            Map<String, Evidence> evidenceByKey,
            JavaSourceFactContext context,
            JspFormInfo form,
            JspInputInfo input) {
        Evidence evidence = evidence(context, input.location());
        FactRecord fact = FactRecord.create(
                identitySourceRoots(context),
                jspInputId(context, form, input),
                requestParameterId(context, input.logicalName()),
                "BINDS_TO",
                "jsp-input",
                context.projectId(),
                context.snapshotId(),
                context.analysisRunId(),
                context.scopeRunId(),
                ANALYZER_ID,
                context.scopeKey(),
                evidence.evidenceKey(),
                Confidence.LIKELY,
                100,
                SourceType.JSP_TOKEN);
        addFact(facts, factKeys, evidenceByKey, evidence, fact);
    }

    private static void addForwardFact(
            List<FactRecord> facts,
            Set<String> factKeys,
            Map<String, Evidence> evidenceByKey,
            JavaSourceFactContext context,
            JspForwardInfo forward) {
        if (isExternalUrl(forward.path()) || isNonRoutableTarget(forward.path()) || isDynamicTarget(forward.path())) {
            return;
        }
        Evidence evidence = evidence(context, forward.location());
        FactRecord fact = FactRecord.create(
                identitySourceRoots(context),
                jspPageId(context, forward.location().relativePath()),
                forwardTargetId(context, forward.location().relativePath(), forward.path()),
                "FORWARDS_TO",
                "jsp-forward",
                context.projectId(),
                context.snapshotId(),
                context.analysisRunId(),
                context.scopeRunId(),
                ANALYZER_ID,
                context.scopeKey(),
                evidence.evidenceKey(),
                Confidence.LIKELY,
                100,
                SourceType.JSP_TOKEN);
        addFact(facts, factKeys, evidenceByKey, evidence, fact);
    }

    private static void addIncludeFact(
            List<FactRecord> facts,
            Set<String> factKeys,
            Map<String, Evidence> evidenceByKey,
            JavaSourceFactContext context,
            JspIncludeInfo include) {
        if (isExternalUrl(include.path()) || isNonRoutableTarget(include.path()) || isDynamicTarget(include.path())) {
            return;
        }
        Evidence evidence = evidence(context, include.location());
        FactRecord fact = FactRecord.create(
                identitySourceRoots(context),
                jspPageId(context, include.location().relativePath()),
                includeTargetId(context, include.location().relativePath(), include.path()),
                "INCLUDES",
                "jsp-include:" + include.kind().name().toLowerCase(),
                context.projectId(),
                context.snapshotId(),
                context.analysisRunId(),
                context.scopeRunId(),
                ANALYZER_ID,
                context.scopeKey(),
                evidence.evidenceKey(),
                Confidence.LIKELY,
                100,
                SourceType.JSP_TOKEN);
        addFact(facts, factKeys, evidenceByKey, evidence, fact);
    }

    private static void addFact(
            List<FactRecord> facts,
            Set<String> factKeys,
            Map<String, Evidence> evidenceByKey,
            Evidence evidence,
            FactRecord fact) {
        if (!factKeys.add(fact.factKey())) {
            return;
        }
        evidenceByKey.putIfAbsent(evidence.evidenceKey(), evidence);
        facts.add(fact);
    }

    private static Evidence evidence(JavaSourceFactContext context, SourceLocation location) {
        return Evidence.create(
                ANALYZER_ID,
                context.scopeKey(),
                location.relativePath(),
                "line:" + location.line(),
                1,
                SourceType.JSP_TOKEN);
    }

    private static String jspFormId(JavaSourceFactContext context, JspFormInfo form) {
        return "jsp-form://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.sourceRootKey() + "/" + form.location().relativePath()
                + "#form[" + formKey(form) + "]";
    }

    private static String jspPageId(JavaSourceFactContext context, String jspPath) {
        return "jsp-page://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.sourceRootKey() + "/" + jspPath;
    }

    private static String htmlPageId(JavaSourceFactContext context, String htmlPath) {
        return "html-page://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.sourceRootKey() + "/" + htmlPath;
    }

    private static String jspInputId(JavaSourceFactContext context, JspFormInfo form, JspInputInfo input) {
        return "jsp-input://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.sourceRootKey() + "/" + input.location().relativePath()
                + "#form[" + formKey(form) + "]:input[" + input.logicalName() + ":"
                + input.inputType() + ":" + input.location().line() + ":0]";
    }

    private static String formKey(JspFormInfo form) {
        return identityFormAction(form.action()) + ":" + form.method() + ":" + form.location().line()
                + ":" + form.location().column();
    }

    private static String identityFormAction(String action) {
        String stripped = stripQueryAndFragment(action);
        if (isDynamicTarget(stripped)) {
            return "_dynamic";
        }
        if (isExternalUrl(stripped)) {
            return "/" + normalizeRoute(stripped);
        }
        if (stripped.startsWith("/")) {
            return "/" + normalizeLocalPath(stripped);
        }
        return stripped;
    }

    private static String rawFormKey(JspFormInfo form) {
        return form.action() + ":" + form.method() + ":" + form.location().line()
                + ":" + form.location().column();
    }

    private static String routeTargetId(
            JavaSourceFactContext context,
            String pagePath,
            String action,
            String method,
            String sourceTag) {
        String resolvedAction = resolveAgainstPage(pagePath, action);
        if (isStrutsAction(sourceTag, resolvedAction)) {
            return actionPathId(context, modulePrefixedStrutsAction(pagePath, sourceTag, resolvedAction));
        }
        return apiEndpointId(context, method, resolvedAction);
    }

    private static String forwardTargetId(JavaSourceFactContext context, String pagePath, String path) {
        return includeTargetId(context, pagePath, path);
    }

    private static String includeTargetId(JavaSourceFactContext context, String pagePath, String path) {
        String resolvedPath = resolveAgainstPage(pagePath, path);
        if (isStrutsAction(resolvedPath)) {
            return actionPathId(context, resolvedPath);
        }
        if (isJspPath(resolvedPath)) {
            return jspPageId(context, normalizeRoute(resolvedPath));
        }
        if (isHtmlPath(resolvedPath)) {
            return htmlPageId(context, normalizeRoute(resolvedPath));
        }
        return apiEndpointId(context, "GET", resolvedPath);
    }

    private static String actionPathId(JavaSourceFactContext context, String action) {
        return "action-path://" + context.projectId() + "/" + context.moduleKey() + "/"
                + ACTION_PATH_SOURCE_ROOT + "/" + normalizedActionPath(action);
    }

    private static String apiEndpointId(JavaSourceFactContext context, String method, String url) {
        return "api-endpoint://" + context.projectId() + "/" + context.moduleKey() + "/"
                + API_ENDPOINT_SOURCE_ROOT + "/" + normalizedMethod(method) + ":" + ensureLeadingSlash(url);
    }

    private static String normalizedActionPath(String action) {
        String normalized = normalizeRoute(action);
        if (normalized.endsWith(".do")) {
            normalized = normalized.substring(0, normalized.length() - ".do".length());
        }
        return normalized;
    }

    private static String requestParameterId(JavaSourceFactContext context, String name) {
        return "request-param://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.sourceRootKey() + "/" + name;
    }

    private static boolean isStrutsAction(String action) {
        String normalized = stripQueryAndFragment(action);
        return !isExternalUrl(normalized) && normalized.endsWith(".do");
    }

    private static boolean isStrutsAction(String sourceTag, String action) {
        String normalized = stripQueryAndFragment(action);
        return !isExternalUrl(normalized)
                && (normalized.endsWith(".do")
                || (isStrutsFormTag(sourceTag)
                && !normalized.isBlank()
                && !isJspPath(normalized)
                && !isHtmlPath(normalized)));
    }

    private static boolean isStrutsFormTag(String sourceTag) {
        return "html:form".equalsIgnoreCase(sourceTag == null ? "" : sourceTag);
    }

    private static String modulePrefixedStrutsAction(String pagePath, String sourceTag, String action) {
        String moduleKey = isStrutsFormTag(sourceTag) ? inferredStrutsModuleKey(pagePath) : "";
        if (moduleKey.isBlank()) {
            return action;
        }
        String normalized = stripQueryAndFragment(action).replace('\\', '/');
        if (normalized.equals(moduleKey) || normalized.startsWith(moduleKey + "/")) {
            return action;
        }
        return moduleKey + "/" + normalized;
    }

    private static String inferredStrutsModuleKey(String pagePath) {
        String normalized = stripQueryAndFragment(pagePath).replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        int webInfStart = normalized.indexOf("/WEB-INF/");
        if (webInfStart <= 0) {
            return "";
        }
        return normalized.substring(0, webInfStart);
    }

    private static boolean isJspPath(String path) {
        String normalized = stripQueryAndFragment(path).toLowerCase();
        return normalized.endsWith(".jsp") || normalized.endsWith(".jspx") || normalized.endsWith(".jspf");
    }

    private static boolean isHtmlPath(String path) {
        String normalized = stripQueryAndFragment(path).toLowerCase();
        return normalized.endsWith(".html") || normalized.endsWith(".htm");
    }

    private static String normalizedMethod(String method) {
        String normalized = method == null || method.isBlank() ? "GET" : method.trim().toUpperCase();
        return normalized.equals("GET") || normalized.equals("POST") || normalized.equals("PUT")
                || normalized.equals("DELETE") || normalized.equals("PATCH") || normalized.equals("HEAD")
                || normalized.equals("OPTIONS") || normalized.equals("TRACE") ? normalized : "GET";
    }

    private static String ensureLeadingSlash(String path) {
        String stripped = stripQueryAndFragment(path);
        if (stripped.isBlank() || stripped.equals("/")) {
            return "/";
        }
        String normalized = normalizeRoute(stripped);
        return normalized.equals("_root") ? "/" : "/" + normalized;
    }

    private static List<String> identitySourceRoots(JavaSourceFactContext context) {
        return EntryPointIds.withEntryPointRoot(List.of(context.sourceRootKey(), API_ENDPOINT_SOURCE_ROOT));
    }

    private static String localSourcePath(JavaSourceFactContext context, String path) {
        String normalized = stripQueryAndFragment(path).replace('\\', '/');
        String sourceRoot = context.sourceRootKey().replace('\\', '/');
        if (normalized.equals(sourceRoot)) {
            return "";
        }
        if (normalized.startsWith(sourceRoot + "/")) {
            return normalized.substring(sourceRoot.length() + 1);
        }
        return normalized;
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

    private static boolean isNonRoutableTarget(String path) {
        return isInPageNavigation(path) || isNonHttpScheme(path);
    }

    private static boolean isDynamicTarget(String path) {
        String normalized = path == null ? "" : path;
        return normalized.contains("${")
                || normalized.contains("#{")
                || normalized.contains("<%")
                || normalized.contains("%>");
    }

    private static boolean isInPageNavigation(String path) {
        String normalized = path == null ? "" : path.trim();
        return normalized.isBlank() || normalized.startsWith("#") || stripQueryAndFragment(normalized).isBlank();
    }

    private static boolean isCurrentPageTarget(String path) {
        String normalized = path == null ? "" : path.trim();
        return normalized.isBlank() || normalized.startsWith("?");
    }

    private static boolean isNonHttpScheme(String path) {
        String normalized = stripQueryAndFragment(path);
        int colon = normalized.indexOf(':');
        if (colon <= 0 || normalized.startsWith("/")) {
            return false;
        }
        for (int i = 0; i < colon; i++) {
            char c = normalized.charAt(i);
            boolean valid = Character.isLetterOrDigit(c) || c == '+' || c == '-' || c == '.';
            if (!valid) {
                return false;
            }
        }
        String scheme = normalized.substring(0, colon).toLowerCase();
        return !scheme.equals("http") && !scheme.equals("https");
    }

    private static String normalizeRoute(String path) {
        String normalized = stripQueryAndFragment(path);
        if (isExternalUrl(normalized)) {
            return externalRoute(normalized);
        }
        normalized = normalizeLocalPath(normalized);
        return normalized.isBlank() ? "_root" : normalized;
    }

    private static String resolveAgainstPage(String pagePath, String target) {
        String normalizedTarget = target == null ? "" : target.trim();
        if (normalizedTarget.isBlank() || normalizedTarget.startsWith("?")) {
            return stripQueryAndFragment(pagePath);
        }
        String stripped = stripQueryAndFragment(normalizedTarget);
        if (stripped.isBlank() || isExternalUrl(stripped) || hasScheme(stripped)) {
            return stripped;
        }
        if (stripped.startsWith("/")) {
            return normalizeLocalPath(stripped);
        }
        String pageDirectory = pageDirectory(pagePath);
        String combined = pageDirectory.isBlank() ? stripped : pageDirectory + "/" + stripped;
        return normalizeLocalPath(combined);
    }

    private static String pageDirectory(String pagePath) {
        String normalized = stripQueryAndFragment(pagePath).replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash <= 0 ? "" : normalized.substring(0, slash);
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

    private static boolean hasScheme(String path) {
        String normalized = stripQueryAndFragment(path);
        int colon = normalized.indexOf(':');
        if (colon <= 0 || normalized.startsWith("/")) {
            return false;
        }
        for (int i = 0; i < colon; i++) {
            char c = normalized.charAt(i);
            boolean valid = Character.isLetterOrDigit(c) || c == '+' || c == '-' || c == '.';
            if (!valid) {
                return false;
            }
        }
        return true;
    }

    private static boolean isExternalUrl(String path) {
        String normalized = path == null ? "" : path.trim();
        if (normalized.startsWith("//")) {
            return true;
        }
        int schemeSeparator = normalized.indexOf("://");
        if (schemeSeparator <= 0) {
            return false;
        }
        for (int i = 0; i < schemeSeparator; i++) {
            char c = normalized.charAt(i);
            boolean valid = Character.isLetterOrDigit(c) || c == '+' || c == '-' || c == '.';
            if (!valid) {
                return false;
            }
        }
        return true;
    }

    private static String externalRoute(String url) {
        String normalized = stripQueryAndFragment(url);
        if (normalized.startsWith("//")) {
            return "external/protocol-relative/" + sanitizeExternalAuthorityAndPath(normalized.substring(2));
        }
        int schemeSeparator = normalized.indexOf("://");
        if (schemeSeparator <= 0) {
            return sanitizeRoutePath(normalized);
        }
        String scheme = sanitizeRoutePath(normalized.substring(0, schemeSeparator).toLowerCase());
        String authorityAndPath = normalized.substring(schemeSeparator + 3);
        return "external/" + scheme + "/" + sanitizeExternalAuthorityAndPath(authorityAndPath);
    }

    private static String sanitizeExternalAuthorityAndPath(String authorityAndPath) {
        String normalized = authorityAndPath == null ? "" : authorityAndPath.trim().replace('\\', '/');
        int slash = normalized.indexOf('/');
        if (slash < 0) {
            return sanitizeRoutePath(normalized);
        }
        String authority = sanitizeRouteSegment(normalized.substring(0, slash));
        String path = sanitizeRoutePath(normalized.substring(slash + 1));
        if (authority.isBlank()) {
            return path;
        }
        return path.equals("_root") ? authority : authority + "/" + path;
    }

    private static String sanitizeRoutePath(String path) {
        String normalized = path == null ? "" : path.trim().replace('\\', '/');
        List<String> segments = new ArrayList<>();
        for (String segment : normalized.split("/")) {
            String sanitized = sanitizeRouteSegment(segment);
            if (sanitized.isBlank() || sanitized.equals(".")) {
                continue;
            }
            if (sanitized.equals("..")) {
                if (!segments.isEmpty()) {
                    segments.remove(segments.size() - 1);
                }
                continue;
            }
            segments.add(sanitized);
        }
        return segments.isEmpty() ? "_root" : String.join("/", segments);
    }

    private static String sanitizeRouteSegment(String path) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            char safe = isRouteSafe(c) ? c : '-';
            builder.append(safe);
        }
        return builder.toString();
    }

    private static boolean isRouteSafe(char c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '/'
                || c == '.'
                || c == '_'
                || c == '-'
                || c == '~';
    }

    private static Map<String, JspFormInfo> formsByKey(List<JspFormInfo> forms) {
        Map<String, JspFormInfo> result = new LinkedHashMap<>();
        for (JspFormInfo form : forms) {
            result.putIfAbsent(rawFormKey(form), form);
        }
        return result;
    }

    private static JspFormInfo ownerForm(String formKey, List<JspFormInfo> forms, Map<String, JspFormInfo> formsByKey) {
        if (!formKey.isBlank()) {
            return formsByKey.get(formKey);
        }
        return null;
    }
}
