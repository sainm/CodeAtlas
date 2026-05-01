package org.sainm.codeatlas.graph.model;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class SymbolIdParser {
    private static final String[] KNOWN_SOURCE_ROOTS = {
        "src/main/java",
        "src/test/java",
        "src/main/webapp",
        "src/test/webapp",
        "src/main/resources",
        "src/test/resources",
        "WEB-INF/classes",
        "WEB-INF/lib",
        "_database",
        "_request",
        "_"
    };
    private static final Map<String, SymbolKind> KINDS = Arrays.stream(SymbolKind.values())
        .collect(Collectors.toUnmodifiableMap(SymbolIdParser::schemeName, kind -> kind));

    private SymbolIdParser() {
    }

    public static SymbolId parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("symbolId is required");
        }
        int schemeIndex = value.indexOf("://");
        if (schemeIndex <= 0) {
            throw new IllegalArgumentException("Unsupported symbolId: " + value);
        }
        SymbolKind kind = KINDS.get(value.substring(0, schemeIndex));
        if (kind == null) {
            throw new IllegalArgumentException("Unsupported symbol kind: " + value);
        }
        String body = value.substring(schemeIndex + 3);
        String[] firstParts = body.split("/", 3);
        if (firstParts.length < 2) {
            throw new IllegalArgumentException("symbolId must include project and module: " + value);
        }
        String projectKey = SymbolId.percentDecode(firstParts[0]);
        String moduleKey = SymbolId.percentDecode(firstParts[1]);
        SourceRest split = splitSourceRoot(firstParts.length > 2 ? firstParts[2] : "_");

        return switch (kind) {
            case METHOD -> parseMethod(projectKey, moduleKey, split.sourceRoot(), split.rest());
            case FIELD -> parseField(projectKey, moduleKey, split.sourceRoot(), split.rest());
            case CLASS, INTERFACE, ENUM, ANNOTATION ->
                new SymbolId(kind, projectKey, moduleKey, split.sourceRoot(), SymbolId.percentDecode(split.rest()), null, null, null);
            default -> parseLogical(kind, projectKey, moduleKey, split.sourceRoot(), split.rest());
        };
    }

    public static String schemeName(SymbolKind kind) {
        return kind.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static SymbolId parseMethod(String projectKey, String moduleKey, String sourceRoot, String rest) {
        int memberIndex = rest.indexOf('#');
        String owner = memberIndex >= 0 ? rest.substring(0, memberIndex) : rest;
        String memberAndDescriptor = memberIndex >= 0 ? rest.substring(memberIndex + 1) : "_";
        int descriptorIndex = memberAndDescriptor.indexOf('(');
        String member = descriptorIndex >= 0 ? memberAndDescriptor.substring(0, descriptorIndex) : memberAndDescriptor;
        String descriptor = descriptorIndex >= 0 ? memberAndDescriptor.substring(descriptorIndex) : "_unknown";
        return SymbolId.method(
            projectKey,
            moduleKey,
            sourceRoot,
            SymbolId.percentDecode(owner),
            SymbolId.percentDecode(member),
            descriptor
        );
    }

    private static SymbolId parseField(String projectKey, String moduleKey, String sourceRoot, String rest) {
        int memberIndex = rest.indexOf('#');
        String owner = memberIndex >= 0 ? rest.substring(0, memberIndex) : rest;
        String memberAndDescriptor = memberIndex >= 0 ? rest.substring(memberIndex + 1) : "_";
        int descriptorIndex = memberAndDescriptor.indexOf(':');
        String member = descriptorIndex >= 0 ? memberAndDescriptor.substring(0, descriptorIndex) : memberAndDescriptor;
        String descriptor = descriptorIndex >= 0 ? memberAndDescriptor.substring(descriptorIndex + 1) : null;
        return SymbolId.field(
            projectKey,
            moduleKey,
            sourceRoot,
            SymbolId.percentDecode(owner),
            SymbolId.percentDecode(member),
            descriptor
        );
    }

    private static SymbolId parseLogical(
        SymbolKind kind,
        String projectKey,
        String moduleKey,
        String sourceRoot,
        String rest
    ) {
        int localIndex = rest.indexOf('#');
        String path = localIndex >= 0 ? rest.substring(0, localIndex) : rest;
        String localId = localIndex >= 0 ? rest.substring(localIndex + 1) : null;
        return SymbolId.logicalPath(
            kind,
            projectKey,
            moduleKey,
            sourceRoot,
            SymbolId.percentDecode(path),
            SymbolId.percentDecode(localId)
        );
    }

    private static SourceRest splitSourceRoot(String sourceAndRest) {
        String normalized = sourceAndRest.replace('\\', '/');
        return Arrays.stream(KNOWN_SOURCE_ROOTS)
            .sorted(Comparator.comparingInt(String::length).reversed())
            .filter(sourceRoot -> normalized.equals(sourceRoot) || normalized.startsWith(sourceRoot + "/"))
            .findFirst()
            .map(sourceRoot -> {
                if (normalized.equals(sourceRoot)) {
                    return new SourceRest(sourceRoot, "");
                }
                return new SourceRest(sourceRoot, normalized.substring(sourceRoot.length() + 1));
            })
            .orElseGet(() -> splitFirstPathSegment(normalized));
    }

    private static SourceRest splitFirstPathSegment(String normalized) {
        int slash = normalized.indexOf('/');
        if (slash < 0) {
            return new SourceRest(SymbolId.percentDecode(normalized), "");
        }
        return new SourceRest(
            SymbolId.percentDecode(normalized.substring(0, slash)),
            normalized.substring(slash + 1)
        );
    }

    private record SourceRest(String sourceRoot, String rest) {
    }
}
