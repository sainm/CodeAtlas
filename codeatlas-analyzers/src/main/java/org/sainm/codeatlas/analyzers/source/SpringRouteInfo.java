package org.sainm.codeatlas.analyzers.source;

import java.util.List;

public record SpringRouteInfo(
        String ownerQualifiedName,
        String methodName,
        String methodSignature,
        List<String> httpMethods,
        List<String> paths,
        SourceLocation location) {
    public SpringRouteInfo {
        JavaClassInfo.requireNonBlank(ownerQualifiedName, "ownerQualifiedName");
        JavaClassInfo.requireNonBlank(methodName, "methodName");
        JavaClassInfo.requireNonBlank(methodSignature, "methodSignature");
        httpMethods = copyRequired(httpMethods, "httpMethods");
        paths = copyRequired(paths, "paths");
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }

    private static List<String> copyRequired(List<String> values, String name) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(name + " are required");
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }
}
