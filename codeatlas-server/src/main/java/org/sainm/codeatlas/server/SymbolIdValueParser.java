package org.sainm.codeatlas.server;

import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.model.SymbolKind;
import java.util.Locale;
import java.util.Map;

public final class SymbolIdValueParser {
    private static final String[] KNOWN_SOURCE_ROOTS = {
        "src/main/java",
        "src/test/java",
        "src/main/webapp",
        "src/test/webapp",
        "src/main/resources",
        "src/test/resources",
        "WEB-INF/classes",
        "_request",
        "_"
    };
    private static final Map<String, SymbolKind> KINDS = java.util.Arrays.stream(SymbolKind.values())
        .collect(java.util.stream.Collectors.toUnmodifiableMap(
            kind -> kind.name().toLowerCase(Locale.ROOT).replace('_', '-'),
            kind -> kind
        ));

    public SymbolId parse(String value) {
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
        String projectKey = firstParts[0];
        String moduleKey = firstParts[1];
        String sourceAndRest = firstParts.length > 2 ? firstParts[2] : "_";
        SourceRest split = splitSourceRoot(sourceAndRest);

        if (kind == SymbolKind.METHOD) {
            return parseMethod(projectKey, moduleKey, split.sourceRoot(), split.rest());
        }
        if (kind == SymbolKind.CLASS || kind == SymbolKind.INTERFACE || kind == SymbolKind.ENUM || kind == SymbolKind.ANNOTATION) {
            return new SymbolId(kind, projectKey, moduleKey, split.sourceRoot(), split.rest(), null, null, null);
        }
        if (kind == SymbolKind.FIELD) {
            return parseField(projectKey, moduleKey, split.sourceRoot(), split.rest());
        }
        String path = split.rest();
        String localId = null;
        int localIndex = path.indexOf('#');
        if (localIndex >= 0) {
            localId = path.substring(localIndex + 1);
            path = path.substring(0, localIndex);
        }
        return SymbolId.logicalPath(kind, projectKey, moduleKey, split.sourceRoot(), path, localId);
    }

    private SymbolId parseMethod(String projectKey, String moduleKey, String sourceRoot, String rest) {
        int memberIndex = rest.indexOf('#');
        String owner = memberIndex >= 0 ? rest.substring(0, memberIndex) : rest;
        String memberAndDesc = memberIndex >= 0 ? rest.substring(memberIndex + 1) : "_";
        int descriptorIndex = memberAndDesc.indexOf('(');
        String member = descriptorIndex >= 0 ? memberAndDesc.substring(0, descriptorIndex) : memberAndDesc;
        String descriptor = descriptorIndex >= 0 ? memberAndDesc.substring(descriptorIndex) : "_unknown";
        return SymbolId.method(projectKey, moduleKey, sourceRoot, owner, member, descriptor);
    }

    private SymbolId parseField(String projectKey, String moduleKey, String sourceRoot, String rest) {
        int memberIndex = rest.indexOf('#');
        String owner = memberIndex >= 0 ? rest.substring(0, memberIndex) : rest;
        String member = memberIndex >= 0 ? rest.substring(memberIndex + 1) : "_";
        return new SymbolId(SymbolKind.FIELD, projectKey, moduleKey, sourceRoot, owner, member, null, null);
    }

    private SourceRest splitSourceRoot(String sourceAndRest) {
        String normalized = sourceAndRest.replace('\\', '/');
        for (String sourceRoot : KNOWN_SOURCE_ROOTS) {
            if (normalized.equals(sourceRoot)) {
                return new SourceRest(sourceRoot, "");
            }
            if (normalized.startsWith(sourceRoot + "/")) {
                return new SourceRest(sourceRoot, normalized.substring(sourceRoot.length() + 1));
            }
        }
        int slash = normalized.indexOf('/');
        if (slash < 0) {
            return new SourceRest(normalized, "");
        }
        return new SourceRest(normalized.substring(0, slash), normalized.substring(slash + 1));
    }

    private record SourceRest(String sourceRoot, String rest) {
    }
}
