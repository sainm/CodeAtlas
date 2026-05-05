package org.sainm.codeatlas.analyzers.source;

public record RequestValueAccessInfo(
        String ownerQualifiedName,
        String methodName,
        String methodSignature,
        String accessKind,
        String name,
        String valueVariableName,
        String resolvedSourceVariableName,
        int line,
        SourceLocation location) {
    public RequestValueAccessInfo {
        JavaClassInfo.requireNonBlank(ownerQualifiedName, "ownerQualifiedName");
        JavaClassInfo.requireNonBlank(methodName, "methodName");
        JavaClassInfo.requireNonBlank(methodSignature, "methodSignature");
        JavaClassInfo.requireNonBlank(accessKind, "accessKind");
        JavaClassInfo.requireNonBlank(name, "name");
        valueVariableName = valueVariableName == null ? "" : valueVariableName;
        resolvedSourceVariableName = resolvedSourceVariableName == null ? "" : resolvedSourceVariableName;
        if (line < 0) {
            throw new IllegalArgumentException("line must be non-negative");
        }
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
