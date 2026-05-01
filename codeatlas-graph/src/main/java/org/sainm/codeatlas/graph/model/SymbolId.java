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
        if (kind == SymbolKind.METHOD && descriptor == null) {
            throw new IllegalArgumentException("method descriptor is required");
        }
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
            builder.append('/').append(encodedOwnerOrPath());
        }
        if (memberName != null) {
            builder.append('#').append(percentEncode(memberName));
        }
        if (descriptor != null) {
            if (kind == SymbolKind.FIELD) {
                builder.append(':');
            }
            builder.append(descriptor);
        }
        if (localId != null) {
            builder.append('#').append(percentEncode(localId));
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
        String[] parts = trimmed.replace('\\', '/').split("/");
        java.util.ArrayDeque<String> normalized = new java.util.ArrayDeque<>();
        for (String part : parts) {
            if (part.isBlank() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (!normalized.isEmpty() && !"..".equals(normalized.peekLast())) {
                    normalized.removeLast();
                    continue;
                }
            }
            normalized.addLast(part);
        }
        return String.join("/", normalized);
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

    private String encodedOwnerOrPath() {
        return switch (kind) {
            case CLASS, INTERFACE, ENUM, ANNOTATION, METHOD, FIELD -> percentEncode(ownerQualifiedName);
            default -> percentEncodePath(ownerQualifiedName);
        };
    }

    static String percentEncodePath(String value) {
        if (value == null) {
            return null;
        }
        return java.util.Arrays.stream(value.split("/", -1))
            .map(SymbolId::percentEncode)
            .collect(java.util.stream.Collectors.joining("/"));
    }

    static String percentEncode(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if (isSafeCodePoint(codePoint)) {
                builder.appendCodePoint(codePoint);
            } else {
                byte[] bytes = new String(Character.toChars(codePoint)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                for (byte b : bytes) {
                    builder.append('%');
                    String hex = Integer.toHexString(b & 0xff).toUpperCase(Locale.ROOT);
                    if (hex.length() == 1) {
                        builder.append('0');
                    }
                    builder.append(hex);
                }
            }
            offset += Character.charCount(codePoint);
        }
        return builder.toString();
    }

    static String percentDecode(String value) {
        if (value == null || !value.contains("%")) {
            return value;
        }
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length();) {
            char current = value.charAt(index);
            if (current == '%' && index + 2 < value.length()) {
                bytes.reset();
                while (index + 2 < value.length() && value.charAt(index) == '%') {
                    int decoded = Integer.parseInt(value.substring(index + 1, index + 3), 16);
                    bytes.write(decoded);
                    index += 3;
                }
                builder.append(bytes.toString(java.nio.charset.StandardCharsets.UTF_8));
                continue;
            }
            builder.append(current);
            index++;
        }
        return builder.toString();
    }

    private static boolean isSafeCodePoint(int codePoint) {
        return (codePoint >= 'a' && codePoint <= 'z')
            || (codePoint >= 'A' && codePoint <= 'Z')
            || (codePoint >= '0' && codePoint <= '9')
            || codePoint == '-'
            || codePoint == '_'
            || codePoint == '.'
            || codePoint == '~'
            || codePoint == '/'
            || codePoint == ':'
            || codePoint == '['
            || codePoint == ']'
            || codePoint == '='
            || codePoint == '!'
            || codePoint == '$'
            || codePoint == ';'
            || codePoint == '('
            || codePoint == ')'
            || codePoint == '<'
            || codePoint == '>';
    }
}
