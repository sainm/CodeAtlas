package org.sainm.codeatlas.analyzers.source;

public record MethodLocalDefUseInfo(
        String ownerQualifiedName,
        String methodName,
        String methodSignature,
        String sourceVariableName,
        String targetVariableName,
        String resolvedSourceVariableName,
        int line,
        SourceLocation location) {
    public MethodLocalDefUseInfo {
        JavaClassInfo.requireNonBlank(ownerQualifiedName, "ownerQualifiedName");
        JavaClassInfo.requireNonBlank(methodName, "methodName");
        JavaClassInfo.requireNonBlank(methodSignature, "methodSignature");
        JavaClassInfo.requireNonBlank(sourceVariableName, "sourceVariableName");
        JavaClassInfo.requireNonBlank(targetVariableName, "targetVariableName");
        resolvedSourceVariableName = resolvedSourceVariableName == null || resolvedSourceVariableName.isBlank()
                ? sourceVariableName
                : resolvedSourceVariableName;
        if (line < 0) {
            throw new IllegalArgumentException("line must be non-negative");
        }
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
