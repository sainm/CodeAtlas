package org.sainm.codeatlas.analyzers.source;

public record RequestDerivedArgumentInfo(
        String ownerQualifiedName,
        String methodName,
        String methodSignature,
        String targetQualifiedName,
        String targetMethodName,
        String targetSignature,
        int argumentIndex,
        String argumentDescriptor,
        String requestParameterName,
        String variableName,
        SourceLocation location) {
    public RequestDerivedArgumentInfo {
        JavaClassInfo.requireNonBlank(ownerQualifiedName, "ownerQualifiedName");
        JavaClassInfo.requireNonBlank(methodName, "methodName");
        JavaClassInfo.requireNonBlank(methodSignature, "methodSignature");
        JavaClassInfo.requireNonBlank(targetQualifiedName, "targetQualifiedName");
        JavaClassInfo.requireNonBlank(targetMethodName, "targetMethodName");
        JavaClassInfo.requireNonBlank(targetSignature, "targetSignature");
        if (argumentIndex < 0) {
            throw new IllegalArgumentException("argumentIndex must be non-negative");
        }
        argumentDescriptor = argumentDescriptor == null || argumentDescriptor.isBlank()
                ? "Ljava/lang/Object;"
                : argumentDescriptor;
        JavaClassInfo.requireNonBlank(requestParameterName, "requestParameterName");
        JavaClassInfo.requireNonBlank(variableName, "variableName");
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
