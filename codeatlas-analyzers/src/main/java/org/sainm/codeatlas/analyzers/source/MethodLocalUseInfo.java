package org.sainm.codeatlas.analyzers.source;

public record MethodLocalUseInfo(
        String ownerQualifiedName,
        String methodName,
        String methodSignature,
        String variableName,
        String resolvedSourceVariableName,
        String usageKind,
        int line,
        SourceLocation location) {
    public MethodLocalUseInfo {
        JavaClassInfo.requireNonBlank(ownerQualifiedName, "ownerQualifiedName");
        JavaClassInfo.requireNonBlank(methodName, "methodName");
        JavaClassInfo.requireNonBlank(methodSignature, "methodSignature");
        JavaClassInfo.requireNonBlank(variableName, "variableName");
        resolvedSourceVariableName = resolvedSourceVariableName == null || resolvedSourceVariableName.isBlank()
                ? variableName
                : resolvedSourceVariableName;
        usageKind = usageKind == null || usageKind.isBlank() ? "read" : usageKind;
        if (line < 0) {
            throw new IllegalArgumentException("line must be non-negative");
        }
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
