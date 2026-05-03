package org.sainm.codeatlas.analyzers.bytecode;

import java.util.List;

public record BytecodeClassInfo(
        String qualifiedName,
        String superClassName,
        List<String> interfaceNames,
        List<String> annotations,
        String originPath) {
    public BytecodeClassInfo {
        requireNonBlank(qualifiedName, "qualifiedName");
        superClassName = superClassName == null ? "" : superClassName;
        interfaceNames = copySorted(interfaceNames);
        annotations = copySorted(annotations);
        originPath = originPath == null ? "" : originPath.replace('\\', '/');
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
