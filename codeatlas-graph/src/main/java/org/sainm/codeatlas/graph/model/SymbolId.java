package org.sainm.codeatlas.graph.model;

import java.util.Locale;
import java.util.Objects;

public record SymbolId(
    SymbolKind kind,
    String projectKey,
    String moduleKey,
    String sourceRootKey,
    String ownerQualifiedName,
    String memberName,
    String descriptor,
    String localId
) {
    public SymbolId {
        Objects.requireNonNull(kind, "kind");
        projectKey = requireKey(projectKey, "projectKey");
        moduleKey = normalizeSegment(defaultIfBlank(moduleKey, "_root"));
        sourceRootKey = normalizePath(defaultIfBlank(sourceRootKey, "_"));
        ownerQualifiedName = normalizeOwnerOrPath(kind, ownerQualifiedName);
        memberName = trimToNull(memberName);
        descriptor = trimToNull(descriptor);
        localId = normalizeLocalId(localId);
    }

    public static SymbolId method(
        String projectKey,
        String moduleKey,
        String sourceRootKey,
        String ownerBinaryName,
        String memberName,
        String jvmDescriptor
    ) {
        return new SymbolId(
            SymbolKind.METHOD,
            projectKey,
            moduleKey,
            sourceRootKey,
            ownerBinaryName,
            memberName,
            jvmDescriptor,
            null
        );
    }

    public static SymbolId classSymbol(
        String projectKey,
        String moduleKey,
        String sourceRootKey,
        String binaryName
    ) {
        return new SymbolId(SymbolKind.CLASS, projectKey, moduleKey, sourceRootKey, binaryName, null, null, null);
    }

    public static SymbolId field(
        String projectKey,
        String moduleKey,
        String sourceRootKey,
        String ownerBinaryName,
        String memberName,
        String jvmDescriptor
    ) {
        return new SymbolId(
            SymbolKind.FIELD,
            projectKey,
            moduleKey,
            sourceRootKey,
            ownerBinaryName,
            memberName,
            jvmDescriptor,
            null
        );
    }

    public static SymbolId logicalPath(
        SymbolKind kind,
        String projectKey,
        String moduleKey,
        String sourceRootKey,
        String path,
        String localId
    ) {
        return new SymbolId(kind, projectKey, moduleKey, sourceRootKey, normalizePath(path), null, null, localId);
    }

    public String value() {
        StringBuilder builder = new StringBuilder();
        builder.append(kind.name().toLowerCase(Locale.ROOT).replace('_', '-'))
            .append("://")
            .append(projectKey)
            .append('/')
            .append(moduleKey)
            .append('/')
            .append(sourceRootKey);

        if (ownerQualifiedName != null) {
            builder.append('/').append(ownerQualifiedName);
        }
        if (memberName != null) {
            builder.append('#').append(memberName);
        }
        if (descriptor != null) {
            builder.append(descriptor);
        }
        if (localId != null) {
            builder.append('#').append(localId);
        }
        return builder.toString();
    }

    public String normalizedPathLower() {
        return value().toLowerCase(Locale.ROOT);
    }

    private static String requireKey(String value, String name) {
        String normalized = normalizeSegment(value);
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return normalized;
    }

    private static String normalizeOwnerOrPath(SymbolKind kind, String owner) {
        String trimmed = trimToNull(owner);
        if (trimmed == null) {
            return null;
        }
        return switch (kind) {
            case CLASS, INTERFACE, ENUM, ANNOTATION, METHOD, FIELD -> trimmed.replace('/', '.');
            default -> normalizePath(trimmed);
        };
    }

    private static String normalizeLocalId(String localId) {
        String trimmed = trimToNull(localId);
        return trimmed == null ? null : trimmed.replace('\\', '/');
    }

    public static String normalizePath(String path) {
        String trimmed = trimToNull(path);
        if (trimmed == null) {
            return null;
        }
        String normalized = trimmed.replace('\\', '/');
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String normalizeSegment(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : normalizePath(trimmed);
    }

    private static String defaultIfBlank(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
