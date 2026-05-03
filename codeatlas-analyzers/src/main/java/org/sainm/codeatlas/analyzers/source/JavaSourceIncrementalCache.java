package org.sainm.codeatlas.analyzers.source;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class JavaSourceIncrementalCache {
    private final Map<String, JavaSourceCacheEntry> entries = new LinkedHashMap<>();

    public Optional<JavaSourceAnalysisResult> getIfHashMatches(String relativePath, String sha256) {
        JavaSourceCacheEntry entry = entries.get(normalize(relativePath));
        if (entry == null || !entry.sha256().equals(sha256)) {
            return Optional.empty();
        }
        return Optional.of(entry.result());
    }

    public void put(String relativePath, String sha256, JavaSourceAnalysisResult result) {
        entries.put(normalize(relativePath), new JavaSourceCacheEntry(relativePath, sha256, result));
    }

    public List<String> cachedPaths() {
        return entries.keySet().stream().sorted().toList();
    }

    public boolean containsAstObjects() {
        return entries.values().stream()
                .map(JavaSourceCacheEntry::result)
                .anyMatch(result -> result.classes().stream().anyMatch(JavaSourceIncrementalCache::isAstObject)
                        || result.methods().stream().anyMatch(JavaSourceIncrementalCache::isAstObject)
                        || result.fields().stream().anyMatch(JavaSourceIncrementalCache::isAstObject)
                        || result.directInvocations().stream().anyMatch(JavaSourceIncrementalCache::isAstObject));
    }

    private static boolean isAstObject(Object value) {
        String className = value.getClass().getName();
        return className.startsWith("spoon.");
    }

    private static String normalize(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("relativePath is required");
        }
        return relativePath.replace('\\', '/');
    }
}
