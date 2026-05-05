package org.sainm.codeatlas.graph;

/**
 * Defines the granularity scope for classpath adjacency cache.
 *
 * <p>When a JAR or module changes, only caches scoped to that specific
 * classpath entry are invalidated, rather than the entire project.
 *
 * <p>Use {@link #projectWide()} when the scope cannot be narrowed
 * (e.g., dependency graph not yet computed), and {@link #forModule(String)}
 * or {@link #forJar(String)} for finer-grained invalidation.
 */
public record ClasspathCacheScope(
        ScopeType type,
        String scopeKey) {

    public enum ScopeType {
        /** Entire project — default backward-compatible scope. */
        PROJECT_WIDE,
        /** Single module (Gradle/Maven module path). */
        MODULE,
        /** Single JAR file (e.g., {@code WEB-INF/lib/vendor.jar}). */
        JAR
    }

    public ClasspathCacheScope {
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        if (type != ScopeType.PROJECT_WIDE && (scopeKey == null || scopeKey.isBlank())) {
            throw new IllegalArgumentException("scopeKey is required for non-project-wide scope");
        }
        if (type == ScopeType.PROJECT_WIDE) {
            scopeKey = "";
        }
    }

    public static ClasspathCacheScope projectWide() {
        return new ClasspathCacheScope(ScopeType.PROJECT_WIDE, "");
    }

    public static ClasspathCacheScope forModule(String modulePath) {
        if (modulePath == null || modulePath.isBlank()) {
            throw new IllegalArgumentException("modulePath is required");
        }
        return new ClasspathCacheScope(ScopeType.MODULE, modulePath);
    }

    public static ClasspathCacheScope forJar(String jarPath) {
        if (jarPath == null || jarPath.isBlank()) {
            throw new IllegalArgumentException("jarPath is required");
        }
        return new ClasspathCacheScope(ScopeType.JAR, jarPath);
    }

    public boolean isProjectWide() {
        return type == ScopeType.PROJECT_WIDE;
    }

    /**
     * Whether this scope covers the given other scope.
     * A project-wide scope covers everything; otherwise, type and key must match.
     */
    public boolean covers(ClasspathCacheScope other) {
        if (other == null) {
            return false;
        }
        if (isProjectWide()) {
            return true;
        }
        return type == other.type && scopeKey.equals(other.scopeKey);
    }

    @Override
    public String toString() {
        return type == ScopeType.PROJECT_WIDE ? "PROJECT_WIDE" : type + ":" + scopeKey;
    }
}
