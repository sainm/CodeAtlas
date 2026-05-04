package org.sainm.codeatlas.symbols;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SymbolIdParser {
    private static final String API_ENDPOINT_SOURCE_ROOT = "_api";
    private static final SymbolIdParser DEFAULT = new SymbolIdParser(
            SymbolKindRegistry.defaults(),
            List.of(
                    "src/main/java",
                    "src/test/java",
                    "src/main/resources",
                    "src/test/resources",
                    "src/main/webapp",
                    "WEB-INF/classes"));

    private final SymbolKindRegistry registry;
    private final List<String> sourceRoots;
    private final List<String> apiEndpointSourceRoots;

    private SymbolIdParser(SymbolKindRegistry registry, List<String> sourceRoots) {
        this.registry = registry;
        this.sourceRoots = normalizeSourceRoots(sourceRoots);
        this.apiEndpointSourceRoots = apiEndpointSourceRoots(this.sourceRoots);
    }

    public static SymbolId parse(String raw) {
        return DEFAULT.parseId(raw);
    }

    public static SymbolIdParser withSourceRoots(List<String> sourceRoots) {
        return new SymbolIdParser(SymbolKindRegistry.defaults(), sourceRoots);
    }

    public SymbolId parseId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Symbol id is required");
        }
        if (raw.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Symbol id must use '/' separators: " + raw);
        }

        int schemeEnd = raw.indexOf("://");
        if (schemeEnd <= 0) {
            throw new IllegalArgumentException("Symbol id must contain a kind scheme: " + raw);
        }

        String kindName = raw.substring(0, schemeEnd);
        SymbolKind kind = registry.require(kindName);
        String rest = raw.substring(schemeEnd + 3);
        ParsedPath parsedPath = parsePath(kind, rest);
        String path = parsedPath.path();
        String fragment = parsedPath.fragment();
        if (fragment != null && !allowsFragment(kind)) {
            throw new IllegalArgumentException("Symbol id kind does not allow a raw fragment: " + raw);
        }

        validateRelativeBasics(path, raw);
        String[] parts = path.split("/", -1);
        if (kindName.equals(DefaultSymbolKind.DATASOURCE.kind())) {
            validateSchemeLikeSeparators(kind, path, null, raw);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Datasource symbol id must contain project and datasource key: " + raw);
            }
            String projectKey = requireSegment(parts[0], "projectKey", raw);
            String datasourceKey = requireSegment(parts[1], "datasourceKey", raw);
            return new SymbolId(kind, projectKey, datasourceKey, "", "", fragment);
        }

        if (parts.length < 3) {
            throw new IllegalArgumentException("Symbol id must contain project, module and owner path: " + raw);
        }

        String projectKey = requireSegment(parts[0], "projectKey", raw);
        String moduleKey = requireSegment(parts[1], "moduleKey", raw);
        ParsedOwner owner = parseOwner(kind, parts, raw);
        validateSchemeLikeSeparators(kind, path, owner, raw);
        return new SymbolId(kind, projectKey, moduleKey, owner.sourceRootKey(), owner.ownerPath(), fragment);
    }

    static SymbolId fromCanonical(String canonical) {
        return parse(canonical);
    }

    private ParsedOwner parseOwner(SymbolKind kind, String[] parts, String raw) {
        String remainder = join(parts, 2, parts.length);
        if (kind.identityType() == IdentityType.ARTIFACT_ID) {
            return new ParsedOwner("", requireSegment(remainder, "artifact path", raw));
        }

        for (String sourceRoot : sourceRootsFor(kind)) {
            if (remainder.equals(sourceRoot)) {
                if (isScopeOnlyFlow(kind)) {
                    continue;
                }
                throw new IllegalArgumentException("Symbol id is missing owner path after source root: " + raw);
            }
            if (remainder.startsWith(sourceRoot + "/")) {
                return new ParsedOwner(sourceRoot, requireOwnerPath(kind, remainder.substring(sourceRoot.length() + 1), raw));
            }
        }

        if (kind.kind().equals(DefaultSymbolKind.API_ENDPOINT.kind())) {
            throw new IllegalArgumentException("API endpoint symbol id must contain a known source root: " + raw);
        }

        int slash = remainder.indexOf('/');
        if (slash < 0 && (kind.kind().equals(DefaultSymbolKind.DB_SCHEMA.kind()) || isScopeOnlyFlow(kind))) {
            return new ParsedOwner("", requireSegment(remainder, "owner path", raw));
        }
        if (slash <= 0 || slash == remainder.length() - 1) {
            throw new IllegalArgumentException("Symbol id has invalid source root and owner path: " + raw);
        }
        return new ParsedOwner(remainder.substring(0, slash), requireOwnerPath(kind, remainder.substring(slash + 1), raw));
    }

    private static String requireOwnerPath(SymbolKind kind, String ownerPath, String raw) {
        requireSegment(ownerPath, "owner path", raw);
        if (ownerPath.startsWith("/") || ownerPath.contains("//")
                || (ownerPath.endsWith("/") && !isApiRootEndpoint(kind, ownerPath))) {
            throw new IllegalArgumentException("Symbol id owner path must be normalized and relative: " + raw);
        }
        return ownerPath;
    }

    private static boolean isApiRootEndpoint(SymbolKind kind, String ownerPath) {
        return kind.kind().equals(DefaultSymbolKind.API_ENDPOINT.kind()) && ownerPath.endsWith(":/");
    }

    private static ParsedPath parsePath(SymbolKind kind, String rest) {
        if (kind.identityType() == IdentityType.FLOW_ID) {
            int flowSuffix = flowSuffixStart(kind, rest);
            if (flowSuffix >= 0) {
                return new ParsedPath(rest.substring(0, flowSuffix), rest.substring(flowSuffix + 1));
            }
        }

        int fragmentStart = rest.indexOf('#');
        if (fragmentStart >= 0) {
            return new ParsedPath(rest.substring(0, fragmentStart), rest.substring(fragmentStart + 1));
        }
        return new ParsedPath(rest, null);
    }

    private static int flowSuffixStart(SymbolKind kind, String rest) {
        return DefaultSymbolKind.from(kind.kind())
                .flatMap(DefaultSymbolKind::flowSuffixToken)
                .map(token -> token.equals("@") ? rest.lastIndexOf(token) : rest.indexOf(token))
                .orElse(-1);
    }

    private static boolean allowsFragment(SymbolKind kind) {
        if (kind.identityType() == IdentityType.FLOW_ID) {
            return true;
        }
        return DefaultSymbolKind.from(kind.kind())
                .map(DefaultSymbolKind::allowsFragment)
                .orElse(false);
    }

    private static boolean isScopeOnlyFlow(SymbolKind kind) {
        return DefaultSymbolKind.from(kind.kind())
                .map(defaultKind -> switch (defaultKind) {
                    case REQUEST_PARAM, REQUEST_ATTR, SESSION_ATTR, MODEL_ATTR -> true;
                    default -> false;
                })
                .orElse(false);
    }

    private List<String> sourceRootsFor(SymbolKind kind) {
        if (kind.kind().equals(DefaultSymbolKind.API_ENDPOINT.kind())) {
            return apiEndpointSourceRoots;
        }
        return sourceRoots;
    }

    private static List<String> apiEndpointSourceRoots(List<String> sourceRoots) {
        List<String> result = new ArrayList<>(sourceRoots);
        if (!result.contains(API_ENDPOINT_SOURCE_ROOT)) {
            result.add(API_ENDPOINT_SOURCE_ROOT);
            result.sort(Comparator.comparingInt(String::length).reversed());
        }
        return List.copyOf(result);
    }

    private static List<String> normalizeSourceRoots(List<String> sourceRoots) {
        if (sourceRoots == null || sourceRoots.isEmpty()) {
            throw new IllegalArgumentException("sourceRoots are required");
        }
        List<String> normalized = new ArrayList<>();
        for (String sourceRoot : sourceRoots) {
            String value = requireSegment(sourceRoot, "source root", String.valueOf(sourceRoots)).replace('\\', '/');
            if (value.startsWith("/") || value.endsWith("/") || value.contains(":/") || hasTraversalSegment(value)) {
                throw new IllegalArgumentException("source root must be logical and relative: " + sourceRoot);
            }
            normalized.add(value);
        }
        normalized.sort(Comparator.comparingInt(String::length).reversed());
        return List.copyOf(normalized);
    }

    private static String requireSegment(String value, String name, String raw) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Symbol id has blank " + name + ": " + raw);
        }
        return value;
    }

    private static String join(String[] parts, int start, int end) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (i > start) {
                builder.append('/');
            }
            builder.append(parts[i]);
        }
        return builder.toString();
    }

    private static void validateRelativeBasics(String path, String raw) {
        if (path.isBlank() || path.startsWith("/") || hasTraversalSegment(path)) {
            throw new IllegalArgumentException("Symbol id path must be logical and relative: " + raw);
        }
    }

    private static void validateSchemeLikeSeparators(
            SymbolKind kind,
            String path,
            ParsedOwner owner,
            String raw) {
        if (!kind.kind().equals(DefaultSymbolKind.API_ENDPOINT.kind())) {
            if (path.contains(":/")) {
                throw new IllegalArgumentException("Symbol id path must be logical and relative: " + raw);
            }
            return;
        }
        if (owner == null || !isValidApiEndpointOwnerPath(owner.ownerPath())) {
            throw new IllegalArgumentException("API endpoint symbol id must contain an HTTP method owner path: " + raw);
        }
        int ownerStart = path.length() - owner.ownerPath().length();
        if (ownerStart < 0 || path.substring(0, ownerStart).contains(":/")) {
            throw new IllegalArgumentException("Symbol id path must be logical and relative: " + raw);
        }
    }

    private static boolean hasTraversalSegment(String path) {
        for (String segment : path.split("/", -1)) {
            if (segment.equals("..")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isValidApiEndpointOwnerPath(String ownerPath) {
        int separator = ownerPath.indexOf(":/");
        return separator > 0
                && ownerPath.indexOf(":/", separator + 2) < 0
                && isHttpMethod(ownerPath.substring(0, separator));
    }

    private static boolean isHttpMethod(String token) {
        return switch (token) {
            case "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS", "TRACE" -> true;
            default -> false;
        };
    }

    private record ParsedOwner(String sourceRootKey, String ownerPath) {
    }

    private record ParsedPath(String path, String fragment) {
    }
}
