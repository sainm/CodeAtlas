package org.sainm.codeatlas.symbols;

import java.util.Optional;

public record SymbolId(
        SymbolKind kind,
        String projectKey,
        String moduleKey,
        String sourceRootKey,
        String ownerPath,
        String fragmentValue) {
    public SymbolId {
        requireNonBlank(projectKey, "projectKey");
        requireNonBlank(moduleKey, "moduleKey");
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        sourceRootKey = sourceRootKey == null ? "" : sourceRootKey;
        ownerPath = ownerPath == null ? "" : ownerPath;
        if (isDatasource(kind) && (!sourceRootKey.isBlank() || !ownerPath.isBlank())) {
            throw new IllegalArgumentException("datasource identity cannot contain sourceRootKey or ownerPath");
        }
        if (ownerPath.isBlank() && requiresOwnerPath(kind)) {
            throw new IllegalArgumentException("ownerPath is required");
        }
        if (fragmentValue != null && !fragmentValue.isBlank() && !allowsFragment(kind)) {
            throw new IllegalArgumentException("fragment is not allowed for kind " + kind.kind());
        }
        requireNormalizedSourceRootKey(sourceRootKey);
        requireNormalizedOwnerPath(ownerPath);
        requireCanonicalSafe(projectKey, "projectKey", false);
        requireCanonicalSafe(moduleKey, "moduleKey", false);
        requireCanonicalSafe(sourceRootKey, "sourceRootKey", false);
        requireCanonicalSafe(ownerPath, "ownerPath", allowsRawHashInOwnerPath(kind));
        requireCanonicalSafe(fragmentValue, "fragment", false);
    }

    public IdentityType identityType() {
        return kind.identityType();
    }

    public Optional<String> fragment() {
        return Optional.ofNullable(fragmentValue).filter(value -> !value.isBlank());
    }

    public String canonical() {
        StringBuilder builder = new StringBuilder()
                .append(kind.kind())
                .append("://")
                .append(projectKey)
                .append("/")
                .append(moduleKey);
        if (!sourceRootKey.isBlank()) {
            builder.append("/").append(sourceRootKey);
        }
        if (!ownerPath.isBlank()) {
            builder.append("/").append(ownerPath);
        }
        fragment().ifPresent(fragment -> builder.append(fragmentDelimiter()).append(fragment));
        return builder.toString();
    }

    private String fragmentDelimiter() {
        return DefaultSymbolKind.from(kind.kind())
                .map(DefaultSymbolKind::fragmentDelimiter)
                .orElse("#");
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private static boolean requiresOwnerPath(SymbolKind kind) {
        return DefaultSymbolKind.from(kind.kind())
                .map(defaultKind -> defaultKind != DefaultSymbolKind.DATASOURCE)
                .orElse(true);
    }

    private static boolean isDatasource(SymbolKind kind) {
        return DefaultSymbolKind.from(kind.kind())
                .map(defaultKind -> defaultKind == DefaultSymbolKind.DATASOURCE)
                .orElse(false);
    }

    private static boolean allowsFragment(SymbolKind kind) {
        return DefaultSymbolKind.from(kind.kind())
                .map(DefaultSymbolKind::allowsFragment)
                .orElse(false);
    }

    private static boolean allowsRawHashInOwnerPath(SymbolKind kind) {
        if (kind.identityType() != IdentityType.FLOW_ID) {
            return false;
        }
        return DefaultSymbolKind.from(kind.kind())
                .map(defaultKind -> !isScopeOnlyFlow(defaultKind))
                .orElse(true);
    }

    private static boolean isScopeOnlyFlow(DefaultSymbolKind kind) {
        return switch (kind) {
            case REQUEST_PARAM, REQUEST_ATTR, SESSION_ATTR, MODEL_ATTR -> true;
            default -> false;
        };
    }

    private static void requireNormalizedSourceRootKey(String sourceRootKey) {
        if (!sourceRootKey.isBlank()
                && (sourceRootKey.startsWith("/") || sourceRootKey.endsWith("/") || sourceRootKey.contains("//"))) {
            throw new IllegalArgumentException("sourceRootKey must be normalized and relative");
        }
    }

    private static void requireNormalizedOwnerPath(String ownerPath) {
        if (!ownerPath.isBlank()
                && (ownerPath.startsWith("/") || ownerPath.endsWith("/") || ownerPath.contains("//"))) {
            throw new IllegalArgumentException("ownerPath must be normalized and relative");
        }
    }

    private static void requireCanonicalSafe(String value, String name, boolean allowRawHash) {
        if (value == null) {
            return;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\'
                    || c == '?'
                    || (c == '#' && !allowRawHash)
                    || c > 0x7f
                    || Character.isWhitespace(c)
                    || Character.isISOControl(c)) {
                throw new IllegalArgumentException(name + " contains an unsafe canonical character");
            }
        }
    }
}
