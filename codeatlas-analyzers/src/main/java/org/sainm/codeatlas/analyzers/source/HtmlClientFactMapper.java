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

public final class HtmlClientFactMapper {
    private static final String ANALYZER_ID = "html-client";
    private static final String ACTION_PATH_SOURCE_ROOT = "_actions";
    private static final String API_ENDPOINT_SOURCE_ROOT = "_api";

    private HtmlClientFactMapper() {
    }

    public static HtmlClientFactMapper defaults() {
        return new HtmlClientFactMapper();
    }

    public JavaSourceFactBatch map(HtmlClientAnalysisResult result, JavaSourceFactContext context) {
        if (result == null) {
            throw new IllegalArgumentException("result is required");
        }
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        List<FactRecord> facts = new ArrayList<>();
        Set<String> factKeys = new HashSet<>();
        Map<String, Evidence> evidenceByKey = new LinkedHashMap<>();
        for (HtmlFormInfo form : result.forms()) {
            addContainsFact(facts, factKeys, evidenceByKey, context, form.location(),
                    htmlPageId(context, form.pagePath()), htmlFormId(context, form));
            addSubmitFact(facts, factKeys, evidenceByKey, context, form);
        }
        Map<String, HtmlFormInfo> formsByKey = formsByKey(result.forms());
        for (HtmlInputInfo input : result.inputs()) {
            HtmlFormInfo ownerForm = ownerForm(input.formKey(), result.forms(), formsByKey);
            if (ownerForm != null) {
                addContainsFact(facts, factKeys, evidenceByKey, context, input.location(),
                        htmlPageId(context, input.pagePath()), htmlInputId(context, ownerForm, input));
                addRenderFact(facts, factKeys, evidenceByKey, context, ownerForm, input);
                addBindFact(facts, factKeys, evidenceByKey, context, ownerForm, input);
            }
        }
        for (ScriptResourceInfo script : result.scripts()) {
            addContainsFact(facts, factKeys, evidenceByKey, context, script.location(),
                    htmlPageId(context, script.pagePath()), scriptResourceId(context, script.pagePath(), script.src()));
            addLoadScriptFact(facts, factKeys, evidenceByKey, context, script);
        }
        for (HtmlLinkInfo link : result.links()) {
            addContainsFact(facts, factKeys, evidenceByKey, context, link.location(),
                    htmlPageId(context, link.pagePath()), htmlLinkId(context, link));
            addNavigationFact(facts, factKeys, evidenceByKey, context, link);
        }
        for (ClientRequestInfo request : result.clientRequests()) {
            addClientRequestFact(facts, factKeys, evidenceByKey, context, request);
        }
        for (DomEventHandlerInfo handler : result.domEventHandlers()) {
            addDomEventFact(facts, factKeys, evidenceByKey, context, handler);
        }
        return new JavaSourceFactBatch(facts, List.copyOf(evidenceByKey.values()));
    }

    private static void addContainsFact(
            List<FactRecord> facts,
            Set<String> factKeys,
            Map<String, Evidence> evidenceByKey,
            JavaSourceFactContext context,
            SourceLocation location,
            String sourceIdentityId,
            String targetIdentityId) {
        addFact(facts, factKeys, evidenceByKey, context, location,
                sourceIdentityId, targetIdentityId, "CONTAINS", "html-structure", Confidence.CERTAIN);
    }

    private static void addSubmitFact(
            List<FactRecord> facts,
            Set<String> factKeys,
            Map<String, Evidence> evidenceByKey,
            JavaSourceFactContext context,
            HtmlFormInfo form) {
        if (!form.action().isBlank() && isNonRoutableTarget(form.action())) {
            return;
        }
        addFact(facts, factKeys, evidenceByKey, context, form.location(),
                htmlFormId(context, form), routeTargetId(context, form.pagePath(), form.action(), form.method()),
                "SUBMITS_TO", "html-form", Confidence.LIKELY);
    }

    private static void addRenderFact(
            List<FactRecord> facts,
            Set<String> factKeys,
            Map<String, Evidence> evidenceByKey,
            JavaSourceFactContext context,
            HtmlFormInfo form,
            HtmlInputInfo input) {
        addFact(facts, factKeys, evidenceByKey, context, input.location(),
                htmlFormId(context, form), htmlInputId(context, form, input),
                "RENDERS_INPUT", "html-form-input", Confidence.CERTAIN);
    }

    private static void addBindFact(
            List<FactRecord> facts,
            Set<String> factKeys,
            Map<String, Evidence> evidenceByKey,
            JavaSourceFactContext context,
            HtmlFormInfo form,
            HtmlInputInfo input) {
        addFact(facts, factKeys, evidenceByKey, context, input.location(),
                htmlInputId(context, form, input), requestParameterId(context, input.logicalName()),
                "BINDS_TO", "html-input", Confidence.LIKELY);
    }

    private static void addLoadScriptFact(
            List<FactRecord> facts,
            Set<String> factKeys,
            Map<String, Evidence> evidenceByKey,
            JavaSourceFactContext context,
            ScriptResourceInfo script) {
        addFact(facts, factKeys, evidenceByKey, context, script.location(),
                htmlPageId(context, script.pagePath()), scriptResourceId(context, script.pagePath(), script.src()),
                "LOADS_SCRIPT", "script-src", Confidence.CERTAIN);
    }

    private static void addNavigationFact(
            List<FactRecord> facts,
            Set<String> factKeys,
            Map<String, Evidence> evidenceByKey,
            JavaSourceFactContext context,
            HtmlLinkInfo link) {
        if (isInPageNavigation(link.href()) || isNonHttpScheme(link.href())) {
            return;
        }
        addFact(facts, factKeys, evidenceByKey, context, link.location(),
                htmlLinkId(context, link), navigationTargetId(context, link.pagePath(), link.href()),
                "NAVIGATES_TO", "html-link", Confidence.LIKELY);
    }

    private static void addClientRequestFact(
            List<FactRecord> facts,
            Set<String> factKeys,
            Map<String, Evidence> evidenceByKey,
            JavaSourceFactContext context,
            ClientRequestInfo request) {
        if (isNonRoutableTarget(request.url()) || !isSupportedHttpMethod(request.httpMethod())) {
            return;
        }
        addFact(facts, factKeys, evidenceByKey, context, request.location(),
                clientRequestId(context, request), requestTargetId(context, request.pagePath(), request.httpMethod(), request.url()),
                "CALLS_HTTP", "fetch", Confidence.LIKELY);
    }

    private static void addDomEventFact(
            List<FactRecord> facts,
            Set<String> factKeys,
            Map<String, Evidence> evidenceByKey,
            JavaSourceFactContext context,
            DomEventHandlerInfo handler) {
        if (handler.relationName().equals("NAVIGATES_TO")
                && (isInPageNavigation(handler.target()) || isNonHttpScheme(handler.target()))) {
            return;
        }
        if (handler.relationName().equals("CALLS_HTTP")
                && (isNonRoutableTarget(handler.target()) || !isSupportedHttpMethod(handler.httpMethod()))) {
            return;
        }
        addFact(facts, factKeys, evidenceByKey, context, handler.location(),
                domEventHandlerId(context, handler), domEventTargetId(context, handler),
                handler.relationName(), "dom-event", Confidence.LIKELY);
    }

    private static void addFact(
            List<FactRecord> facts,
            Set<String> factKeys,
            Map<String, Evidence> evidenceByKey,
            JavaSourceFactContext context,
            SourceLocation location,
            String sourceIdentityId,
            String targetIdentityId,
            String relationName,
            String qualifier,
            Confidence confidence) {
        Evidence evidence = Evidence.create(
                ANALYZER_ID,
                context.scopeKey(),
                location.relativePath(),
                "line:" + location.line(),
                1,
                SourceType.HTML_TOKEN);
        FactRecord fact = FactRecord.create(
                identitySourceRoots(context),
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
                SourceType.HTML_TOKEN);
        if (!factKeys.add(fact.factKey())) {
            return;
        }
        evidenceByKey.putIfAbsent(evidence.evidenceKey(), evidence);
        facts.add(fact);
    }

    private static String htmlPageId(JavaSourceFactContext context, String pagePath) {
        return "html-page://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.sourceRootKey() + "/" + normalizeRelative(pagePath);
    }

    private static String htmlFormId(JavaSourceFactContext context, HtmlFormInfo form) {
        return "html-form://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.sourceRootKey() + "/" + normalizeRelative(form.pagePath())
                + "#form[" + formKey(form) + "]";
    }

    private static String htmlInputId(JavaSourceFactContext context, HtmlFormInfo form, HtmlInputInfo input) {
        return "html-input://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.sourceRootKey() + "/" + normalizeRelative(input.pagePath())
                + "#form[" + formKey(form) + "]:input[" + input.logicalName() + ":"
                + input.inputType() + ":" + input.location().line() + ":0]";
    }

    private static String htmlLinkId(JavaSourceFactContext context, HtmlLinkInfo link) {
        return "html-link://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.sourceRootKey() + "/" + normalizeRelative(link.pagePath())
                + "#link[" + stripQueryAndFragment(link.href()) + ":" + link.location().line() + ":0]";
    }

    private static String scriptResourceId(JavaSourceFactContext context, String pagePath, String src) {
        return "script-resource://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.sourceRootKey() + "/" + normalizeRoute(resolveAgainstPage(pagePath, src));
    }

    private static String clientRequestId(JavaSourceFactContext context, ClientRequestInfo request) {
        return "client-request://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.sourceRootKey() + "/" + normalizeRelative(request.pagePath())
                + "#fetch[" + request.httpMethod() + ":" + ensureLeadingSlash(resolveAgainstPage(request.pagePath(), request.url())) + ":"
                + request.location().line() + ":0]";
    }

    private static String domEventHandlerId(JavaSourceFactContext context, DomEventHandlerInfo handler) {
        return "dom-event-handler://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.sourceRootKey() + "/" + normalizeRelative(handler.pagePath())
                + "#event[" + handler.eventName() + ":" + handler.location().line() + ":" + handler.location().column() + "]";
    }

    private static String domEventTargetId(JavaSourceFactContext context, DomEventHandlerInfo handler) {
        if (handler.relationName().equals("CALLS_HTTP")) {
            return requestTargetId(context, handler.pagePath(), handler.httpMethod(), handler.target());
        }
        return navigationTargetId(context, handler.pagePath(), handler.target());
    }

    private static String routeTargetId(JavaSourceFactContext context, String pagePath, String action, String method) {
        String resolvedAction = resolveAgainstPage(pagePath, action);
        if (isStrutsAction(resolvedAction)) {
            return actionPathId(context, resolvedAction);
        }
        return apiEndpointId(context, method.toUpperCase(), resolvedAction);
    }

    private static String navigationTargetId(JavaSourceFactContext context, String pagePath, String href) {
        String normalizedHref = resolveAgainstPage(pagePath, href);
        if (!isExternalUrl(normalizedHref) && (normalizedHref.endsWith(".html") || normalizedHref.endsWith(".htm"))) {
            return htmlPageId(context, normalizeRoute(normalizedHref));
        }
        if (isStrutsAction(normalizedHref)) {
            return actionPathId(context, normalizedHref);
        }
        return apiEndpointId(context, "GET", normalizedHref);
    }

    private static String requestTargetId(JavaSourceFactContext context, String pagePath, String method, String url) {
        String resolvedUrl = resolveAgainstPage(pagePath, url);
        if (isStrutsAction(resolvedUrl)) {
            return actionPathId(context, resolvedUrl);
        }
        return apiEndpointId(context, method, resolvedUrl);
    }

    private static String actionPathId(JavaSourceFactContext context, String action) {
        return "action-path://" + context.projectId() + "/" + context.moduleKey() + "/"
                + ACTION_PATH_SOURCE_ROOT + "/" + stripActionExtension(normalizeRoute(action));
    }

    private static String apiEndpointId(JavaSourceFactContext context, String method, String url) {
        return "api-endpoint://" + context.projectId() + "/" + context.moduleKey() + "/"
                + API_ENDPOINT_SOURCE_ROOT + "/" + normalizedMethod(method) + ":" + ensureLeadingSlash(url);
    }

    private static String requestParameterId(JavaSourceFactContext context, String name) {
        return "request-param://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.sourceRootKey() + "/" + name;
    }

    private static String formKey(HtmlFormInfo form) {
        return identityFormAction(form.action()) + ":" + form.method() + ":" + form.location().line()
                + ":" + form.location().column();
    }

    private static String identityFormAction(String action) {
        String stripped = stripQueryAndFragment(action);
        if (isExternalUrl(stripped)) {
            return "/" + normalizeRoute(stripped);
        }
        if (stripped.startsWith("/")) {
            return "/" + normalizeLocalPath(stripped);
        }
        return stripped;
    }

    private static String rawFormKey(HtmlFormInfo form) {
        return form.action() + ":" + form.method() + ":" + form.location().line()
                + ":" + form.location().column();
    }

    private static String stripActionExtension(String path) {
        return path.endsWith(".do") ? path.substring(0, path.length() - ".do".length()) : path;
    }

    private static boolean isStrutsAction(String path) {
        String normalized = stripQueryAndFragment(path);
        return !isExternalUrl(normalized) && normalized.endsWith(".do");
    }

    private static String normalizeRoute(String path) {
        String normalized = stripQueryAndFragment(path);
        if (isExternalUrl(normalized)) {
            return externalRoute(normalized);
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.isBlank() ? "_root" : normalized;
    }

    private static String normalizeRelative(String path) {
        return normalizeRoute(path);
    }

    private static String resolveAgainstPage(String pagePath, String target) {
        String normalizedTarget = target == null ? "" : target.trim();
        if (normalizedTarget.isBlank() || normalizedTarget.startsWith("?")) {
            return stripQueryAndFragment(pagePath);
        }
        String stripped = stripQueryAndFragment(normalizedTarget);
        if (isExternalUrl(stripped) || hasScheme(stripped)) {
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

    private static String ensureLeadingSlash(String path) {
        String stripped = stripQueryAndFragment(path);
        if (stripped.isBlank() || stripped.equals("/")) {
            return "/";
        }
        String normalized = normalizeRoute(stripped);
        return normalized.equals("_root") ? "/" : "/" + normalized;
    }

    private static String normalizedMethod(String method) {
        String normalized = method == null || method.isBlank() ? "GET" : method.trim().toUpperCase();
        return isSupportedHttpMethod(normalized) ? normalized : "GET";
    }

    private static boolean isSupportedHttpMethod(String method) {
        String normalized = method == null ? "" : method.trim().toUpperCase();
        return normalized.equals("GET") || normalized.equals("POST") || normalized.equals("PUT")
                || normalized.equals("DELETE") || normalized.equals("PATCH") || normalized.equals("HEAD")
                || normalized.equals("OPTIONS") || normalized.equals("TRACE");
    }

    private static List<String> identitySourceRoots(JavaSourceFactContext context) {
        return List.of(context.sourceRootKey(), API_ENDPOINT_SOURCE_ROOT);
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

    private static boolean isInPageNavigation(String path) {
        String normalized = path == null ? "" : path.trim();
        return normalized.isBlank() || normalized.startsWith("#");
    }

    private static boolean isNonRoutableTarget(String path) {
        return isInPageNavigation(path) || isNonHttpScheme(path);
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

    private static Map<String, HtmlFormInfo> formsByKey(List<HtmlFormInfo> forms) {
        Map<String, HtmlFormInfo> result = new LinkedHashMap<>();
        for (HtmlFormInfo form : forms) {
            result.putIfAbsent(rawFormKey(form), form);
        }
        return result;
    }

    private static HtmlFormInfo ownerForm(String formKey, List<HtmlFormInfo> forms, Map<String, HtmlFormInfo> formsByKey) {
        if (!formKey.isBlank()) {
            return formsByKey.get(formKey);
        }
        return null;
    }
}
