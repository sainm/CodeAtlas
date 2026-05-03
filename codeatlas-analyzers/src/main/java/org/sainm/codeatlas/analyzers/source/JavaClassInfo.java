package org.sainm.codeatlas.analyzers.source;

import java.util.List;

public record JavaClassInfo(
        String qualifiedName,
        String simpleName,
        List<String> annotations,
        SourceLocation location) {
    public JavaClassInfo {
        requireNonBlank(qualifiedName, "qualifiedName");
        requireNonBlank(simpleName, "simpleName");
        annotations = copySorted(annotations);
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }

    static List<String> copySorted(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
