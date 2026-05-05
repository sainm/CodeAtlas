package org.sainm.codeatlas.symbols;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

public final class SymbolIdNormalizer {
    private static final List<String> DEFAULT_JAVA_SOURCE_ROOTS = List.of("src/main/java");

    private SymbolIdNormalizer() {
    }

    public static SymbolId javaMethod(
            SymbolContext context,
            String physicalPath,
            String ownerBinaryName,
            String methodName,
            String erasedDescriptor) {
        return javaMethod(context, DEFAULT_JAVA_SOURCE_ROOTS, physicalPath, ownerBinaryName, methodName, erasedDescriptor);
    }

    public static SymbolId javaMethod(
            SymbolContext context,
            List<String> sourceRoots,
            String physicalPath,
            String ownerBinaryName,
            String methodName,
            String erasedDescriptor) {
        requireNonBlank(physicalPath, "physicalPath");
        requireNonBlank(ownerBinaryName, "ownerBinaryName");
        requireNonBlank(methodName, "methodName");
        requireNonBlank(erasedDescriptor, "erasedDescriptor");

        String relative = relativize(context.workspaceRoot(), physicalPath);
        String normalizedRelative = normalizeSeparators(relative);
        List<String> normalizedSourceRoots = normalizeSourceRoots(sourceRoots);
        String sourceRootKey = findSourceRoot(normalizedRelative, normalizedSourceRoots, physicalPath);

        String canonical = "method://"
                + context.projectKey()
                + "/"
                + context.moduleKey()
                + "/"
                + sourceRootKey
                + "/"
                + ownerBinaryName
                + "#"
                + methodName
                + erasedDescriptor;
        return SymbolIdParser.withSourceRoots(normalizedSourceRoots).parseId(canonical);
    }

    public static ProvisionalSymbol provisionalJavaMethod(
            SymbolContext context,
            List<String> sourceRoots,
            String physicalPath,
            String ownerBinaryName,
            String methodName,
            String sourceDescriptorText) {
        requireNonBlank(sourceDescriptorText, "sourceDescriptorText");
        String provisionalDescriptor = "(U" + stableHash(sourceDescriptorText) + ")U";
        SymbolId symbolId = javaMethod(
                context,
                sourceRoots,
                physicalPath,
                ownerBinaryName,
                methodName,
                provisionalDescriptor);
        return ProvisionalSymbol.unresolvedDescriptor(symbolId, "descriptorStatus=UNRESOLVED");
    }

    public static SymbolId jspPage(SymbolContext context, List<String> sourceRoots, String physicalPath) {
        return fileSymbol(DefaultSymbolKind.JSP_PAGE, context, sourceRoots, physicalPath, null);
    }

    public static SymbolId jspForm(
            SymbolContext context,
            List<String> sourceRoots,
            String physicalPath,
            String stableFormKey) {
        requireNonBlank(stableFormKey, "stableFormKey");
        return fileSymbol(DefaultSymbolKind.JSP_FORM, context, sourceRoots, physicalPath, "form[" + stableFormKey + "]");
    }

    public static SymbolId jspInput(
            SymbolContext context,
            List<String> sourceRoots,
            String physicalPath,
            String stableFormKey,
            String stableInputKey) {
        requireNonBlank(stableFormKey, "stableFormKey");
        requireNonBlank(stableInputKey, "stableInputKey");
        return fileSymbol(
                DefaultSymbolKind.JSP_INPUT,
                context,
                sourceRoots,
                physicalPath,
                "form[" + stableFormKey + "]",
                "input[" + stableInputKey + "]");
    }

    public static SymbolId jspTag(
            SymbolContext context,
            List<String> sourceRoots,
            String physicalPath,
            String prefix,
            String name,
            int line,
            int ordinal) {
        requireNonBlank(prefix, "prefix");
        requireNonBlank(name, "name");
        requirePositive(line, "line");
        requireNonNegative(ordinal, "ordinal");
        return fileSymbol(
                DefaultSymbolKind.JSP_TAG,
                context,
                sourceRoots,
                physicalPath,
                "tag[" + prefix + ":" + name + ":" + line + ":" + ordinal + "]");
    }

    public static SymbolId htmlPage(SymbolContext context, List<String> sourceRoots, String physicalPath) {
        return fileSymbol(DefaultSymbolKind.HTML_PAGE, context, sourceRoots, physicalPath, null);
    }

    public static SymbolId htmlForm(
            SymbolContext context,
            List<String> sourceRoots,
            String physicalPath,
            String stableFormKey) {
        requireNonBlank(stableFormKey, "stableFormKey");
        return fileSymbol(DefaultSymbolKind.HTML_FORM, context, sourceRoots, physicalPath, "form[" + stableFormKey + "]");
    }

    public static SymbolId htmlInput(
            SymbolContext context,
            List<String> sourceRoots,
            String physicalPath,
            String stableFormKey,
            String stableInputKey) {
        requireNonBlank(stableFormKey, "stableFormKey");
        requireNonBlank(stableInputKey, "stableInputKey");
        return fileSymbol(
                DefaultSymbolKind.HTML_INPUT,
                context,
                sourceRoots,
                physicalPath,
                "form[" + stableFormKey + "]",
                "input[" + stableInputKey + "]");
    }

    public static SymbolId scriptResource(SymbolContext context, List<String> sourceRoots, String physicalPath) {
        return fileSymbol(DefaultSymbolKind.SCRIPT_RESOURCE, context, sourceRoots, physicalPath, null);
    }

    public static SymbolId clientRequest(
            SymbolContext context,
            List<String> sourceRoots,
            String physicalPath,
            String requestKind,
            String method,
            String urlHash,
            int line,
            int ordinal) {
        requireNonBlank(requestKind, "requestKind");
        requireNonBlank(method, "method");
        requireNonBlank(urlHash, "urlHash");
        requirePositive(line, "line");
        requireNonNegative(ordinal, "ordinal");
        return fileSymbol(
                DefaultSymbolKind.CLIENT_REQUEST,
                context,
                sourceRoots,
                physicalPath,
                "request[" + requestKind + ":" + method + ":" + urlHash + ":" + line + ":" + ordinal + "]");
    }

    public static SymbolId sqlStatement(
            SymbolContext context,
            List<String> sourceRoots,
            String physicalPath,
            String statementIdOrHash) {
        requireNonBlank(statementIdOrHash, "statementIdOrHash");
        return fileSymbol(DefaultSymbolKind.SQL_STATEMENT, context, sourceRoots, physicalPath, statementIdOrHash);
    }

    public static SymbolId configKey(
            SymbolContext context,
            List<String> sourceRoots,
            String physicalPath,
            String xmlOrPropertyPath) {
        requireNonBlank(xmlOrPropertyPath, "xmlOrPropertyPath");
        return fileSymbol(DefaultSymbolKind.CONFIG_KEY, context, sourceRoots, physicalPath, xmlOrPropertyPath);
    }

    public static SymbolId reportDefinition(SymbolContext context, List<String> sourceRoots, String physicalPath) {
        return fileSymbol(DefaultSymbolKind.REPORT_DEFINITION, context, sourceRoots, physicalPath, null);
    }

    public static SymbolId reportDefinition(
            SymbolContext context,
            List<String> sourceRoots,
            String physicalPath,
            String reportId) {
        requireNonBlank(reportId, "reportId");
        return fileSymbol(DefaultSymbolKind.REPORT_DEFINITION, context, sourceRoots, physicalPath, reportId);
    }

    public static SymbolId reportField(
            SymbolContext context,
            List<String> sourceRoots,
            String physicalPath,
            String fieldName) {
        requireNonBlank(fieldName, "fieldName");
        return fileSymbol(DefaultSymbolKind.REPORT_FIELD, context, sourceRoots, physicalPath, fieldName);
    }

    // ---- FLOW_ID factories ----

    public static SymbolId paramSlot(
            SymbolContext context,
            List<String> sourceRoots,
            String physicalPath,
            String ownerQualifiedName,
            String methodName,
            String erasedDescriptor,
            int paramIndex,
            String paramTypeDescriptor) {
        String base = ownerQualifiedName + "#" + methodName + erasedDescriptor;
        String fragment = "param[" + paramIndex + ":" + paramTypeDescriptor + "]";
        return flowSymbol(DefaultSymbolKind.PARAM_SLOT, context, sourceRoots, physicalPath, base, fragment);
    }

    public static SymbolId returnSlot(
            SymbolContext context,
            List<String> sourceRoots,
            String physicalPath,
            String ownerQualifiedName,
            String methodName,
            String erasedDescriptor) {
        String base = ownerQualifiedName + "#" + methodName + erasedDescriptor;
        String fragment = "return[" + erasedDescriptor + "]";
        return flowSymbol(DefaultSymbolKind.RETURN_SLOT, context, sourceRoots, physicalPath, base, fragment);
    }

    public static SymbolId requestParam(String projectKey, String scope, String paramName) {
        requireNonBlank(projectKey, "projectKey");
        requireNonBlank(scope, "scope");
        requireNonBlank(paramName, "paramName");
        return flowSymbolScopeOnly(DefaultSymbolKind.REQUEST_PARAM, projectKey, scope, paramName);
    }

    public static SymbolId requestAttr(String projectKey, String scope, String attrName) {
        requireNonBlank(projectKey, "projectKey");
        requireNonBlank(scope, "scope");
        requireNonBlank(attrName, "attrName");
        return flowSymbolScopeOnly(DefaultSymbolKind.REQUEST_ATTR, projectKey, scope, attrName);
    }

    public static SymbolId sessionAttr(String projectKey, String scope, String attrName) {
        requireNonBlank(projectKey, "projectKey");
        requireNonBlank(scope, "scope");
        requireNonBlank(attrName, "attrName");
        return flowSymbolScopeOnly(DefaultSymbolKind.SESSION_ATTR, projectKey, scope, attrName);
    }

    public static SymbolId modelAttr(String projectKey, String scope, String attrName) {
        requireNonBlank(projectKey, "projectKey");
        requireNonBlank(scope, "scope");
        requireNonBlank(attrName, "attrName");
        return flowSymbolScopeOnly(DefaultSymbolKind.MODEL_ATTR, projectKey, scope, attrName);
    }

    public static SymbolId sqlParam(
            SymbolContext context,
            List<String> sourceRoots,
            String physicalPath,
            String statementId,
            String paramName) {
        String fragment = "param[" + paramName + "]";
        return flowSymbol(DefaultSymbolKind.SQL_PARAM, context, sourceRoots, physicalPath, statementId, fragment);
    }

    public static SymbolId sqlBranchCondition(
            SymbolContext context,
            List<String> sourceRoots,
            String physicalPath,
            String statementId,
            String branchId) {
        String fragment = "branch[" + branchId + "]";
        return flowSymbol(DefaultSymbolKind.SQL_BRANCH_CONDITION, context, sourceRoots, physicalPath, statementId, fragment);
    }

    public static SymbolId methodSummary(
            SymbolContext context,
            List<String> sourceRoots,
            String physicalPath,
            String ownerQualifiedName,
            String methodName,
            String erasedDescriptor) {
        String ownerPath = ownerQualifiedName + "#" + methodName + erasedDescriptor;
        return flowSymbol(DefaultSymbolKind.METHOD_SUMMARY, context, sourceRoots, physicalPath, ownerPath, "");
    }

    // ---- ARTIFACT_ID factories ----

    public static SymbolId featureSeed(String projectKey, String runId, String seedHash) {
        return artifactSymbol(DefaultSymbolKind.FEATURE_SEED, projectKey, runId, seedHash);
    }

    public static SymbolId featureScope(String projectKey, String runId, String scopeId) {
        return artifactSymbol(DefaultSymbolKind.FEATURE_SCOPE, projectKey, runId, scopeId);
    }

    public static SymbolId changePlan(String projectKey, String runId, String planId) {
        return artifactSymbol(DefaultSymbolKind.CHANGE_PLAN, projectKey, runId, planId);
    }

    public static SymbolId architectureHealth(String projectKey, String runId, String reportId) {
        return artifactSymbol(DefaultSymbolKind.ARCHITECTURE_HEALTH, projectKey, runId, reportId);
    }

    public static SymbolId importReview(String projectKey, String reviewId) {
        requireNonBlank(projectKey, "projectKey");
        requireNonBlank(reviewId, "reviewId");
        return artifactSymbol(DefaultSymbolKind.IMPORT_REVIEW, projectKey, "_root", reviewId);
    }

    public static SymbolId analysisScopeDecision(String projectKey, String decisionId) {
        requireNonBlank(projectKey, "projectKey");
        requireNonBlank(decisionId, "decisionId");
        return artifactSymbol(DefaultSymbolKind.ANALYSIS_SCOPE_DECISION, projectKey, "_root", decisionId);
    }

    public static SymbolId savedQuery(String projectKey, String queryId) {
        requireNonBlank(projectKey, "projectKey");
        requireNonBlank(queryId, "queryId");
        return artifactSymbol(DefaultSymbolKind.SAVED_QUERY, projectKey, "_root", queryId);
    }

    public static SymbolId watchSubscription(String projectKey, String subscriptionId) {
        requireNonBlank(projectKey, "projectKey");
        requireNonBlank(subscriptionId, "subscriptionId");
        return artifactSymbol(DefaultSymbolKind.WATCH_SUBSCRIPTION, projectKey, "_root", subscriptionId);
    }

    // -- private FLOW_ID / ARTIFACT_ID helpers

    private static SymbolId flowSymbol(
            DefaultSymbolKind kind,
            SymbolContext context,
            List<String> sourceRoots,
            String physicalPath,
            String ownerPath,
            String fragment) {
        SourceOwnedPath filePath = sourceOwnedPath(context, sourceRoots, physicalPath);
        SymbolId symbolId = new SymbolId(
                kind.toSymbolKind(),
                context.projectKey(),
                context.moduleKey(),
                filePath.sourceRootKey(),
                ownerPath,
                fragment);
        return SymbolIdParser.withSourceRoots(filePath.sourceRoots()).parseId(symbolId.canonical());
    }

    private static SymbolId flowSymbolScopeOnly(
            DefaultSymbolKind kind,
            String projectKey,
            String scope,
            String fragmentValue) {
        SymbolId symbolId = new SymbolId(
                kind.toSymbolKind(),
                projectKey,
                "_root",
                "",
                scope,
                fragmentValue);
        return SymbolIdParser.parse(symbolId.canonical());
    }

    private static SymbolId artifactSymbol(
            DefaultSymbolKind kind,
            String projectKey,
            String moduleKey,
            String ownerPath) {
        requireNonBlank(projectKey, "projectKey");
        requireNonBlank(moduleKey, "moduleKey");
        requireNonBlank(ownerPath, "ownerPath");
        SymbolId symbolId = new SymbolId(
                kind.toSymbolKind(),
                projectKey,
                moduleKey,
                "",
                ownerPath,
                null);
        return SymbolIdParser.parse(symbolId.canonical());
    }

    private static SymbolId fileSymbol(
            DefaultSymbolKind kind,
            SymbolContext context,
            List<String> sourceRoots,
            String physicalPath,
            String fragment) {
        SourceOwnedPath path = sourceOwnedPath(context, sourceRoots, physicalPath);
        SymbolId symbolId = new SymbolId(
                kind.toSymbolKind(),
                context.projectKey(),
                context.moduleKey(),
                path.sourceRootKey(),
                path.ownerPath(),
                fragment);
        return SymbolIdParser.withSourceRoots(path.sourceRoots()).parseId(symbolId.canonical());
    }

    private static SymbolId fileSymbol(
            DefaultSymbolKind kind,
            SymbolContext context,
            List<String> sourceRoots,
            String physicalPath,
            String ownerSuffix,
            String fragment) {
        SourceOwnedPath path = sourceOwnedPath(context, sourceRoots, physicalPath);
        SymbolId symbolId = new SymbolId(
                kind.toSymbolKind(),
                context.projectKey(),
                context.moduleKey(),
                path.sourceRootKey(),
                path.ownerPath() + "#" + ownerSuffix,
                fragment);
        return SymbolIdParser.withSourceRoots(path.sourceRoots()).parseId(symbolId.canonical());
    }

    private static SourceOwnedPath sourceOwnedPath(
            SymbolContext context,
            List<String> sourceRoots,
            String physicalPath) {
        requireNonBlank(physicalPath, "physicalPath");
        String relative = relativize(context.workspaceRoot(), physicalPath);
        String normalizedRelative = normalizeSeparators(relative);
        List<String> normalizedSourceRoots = normalizeSourceRoots(sourceRoots);
        String sourceRootKey = findSourceRoot(normalizedRelative, normalizedSourceRoots, physicalPath);
        String ownerPath = normalizedRelative.substring(sourceRootKey.length() + 1);
        requireNonBlank(ownerPath, "ownerPath");
        return new SourceOwnedPath(sourceRootKey, ownerPath, normalizedSourceRoots);
    }

    private static String relativize(String root, String path) {
        String normalizedRoot = normalizeSeparators(root);
        String normalizedPath = normalizeSeparators(path);
        if (normalizedPath.equals(normalizedRoot)) {
            return "";
        }
        String prefix = normalizedRoot.endsWith("/") ? normalizedRoot : normalizedRoot + "/";
        if (!normalizedPath.startsWith(prefix)) {
            throw new IllegalArgumentException("Path is outside workspace root: " + path);
        }
        return normalizedPath.substring(prefix.length());
    }

    private static String normalizeSeparators(String value) {
        return value.replace('\\', '/');
    }

    private static String findSourceRoot(String normalizedRelative, List<String> sourceRoots, String physicalPath) {
        for (String sourceRoot : sourceRoots) {
            if (normalizedRelative.startsWith(sourceRoot + "/")) {
                return sourceRoot;
            }
        }
        throw new IllegalArgumentException("Java method path is not under configured source roots: " + physicalPath);
    }

    private static List<String> normalizeSourceRoots(List<String> sourceRoots) {
        if (sourceRoots == null || sourceRoots.isEmpty()) {
            throw new IllegalArgumentException("sourceRoots are required");
        }
        List<String> normalized = new ArrayList<>();
        for (String sourceRoot : sourceRoots) {
            requireNonBlank(sourceRoot, "sourceRoot");
            String value = normalizeSeparators(sourceRoot);
            if (value.startsWith("/") || value.endsWith("/") || value.contains("//") || value.contains(":/")
                    || value.contains("..")) {
                throw new IllegalArgumentException("source root must be logical and relative: " + sourceRoot);
            }
            normalized.add(value);
        }
        normalized.sort(Comparator.comparingInt(String::length).reversed());
        return List.copyOf(normalized);
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }

    private static String stableHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private record SourceOwnedPath(String sourceRootKey, String ownerPath, List<String> sourceRoots) {
    }
}
