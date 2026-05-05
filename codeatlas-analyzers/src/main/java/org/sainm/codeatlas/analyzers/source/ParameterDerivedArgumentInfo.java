package org.sainm.codeatlas.analyzers.source;

public record ParameterDerivedArgumentInfo(
        String ownerQualifiedName,
        String methodName,
        String methodSignature,
        int sourceParameterIndex,
        String sourceParameterDescriptor,
        String targetQualifiedName,
        String targetMethodName,
        String targetSignature,
        int targetArgumentIndex,
        String targetArgumentDescriptor,
        String variableName,
        SourceLocation location) {
    public ParameterDerivedArgumentInfo {
        JavaClassInfo.requireNonBlank(ownerQualifiedName, "ownerQualifiedName");
        JavaClassInfo.requireNonBlank(methodName, "methodName");
        JavaClassInfo.requireNonBlank(methodSignature, "methodSignature");
        if (sourceParameterIndex < 0 || targetArgumentIndex < 0) {
            throw new IllegalArgumentException("parameter indexes must be non-negative");
        }
        sourceParameterDescriptor = defaultDescriptor(sourceParameterDescriptor);
        JavaClassInfo.requireNonBlank(targetQualifiedName, "targetQualifiedName");
        JavaClassInfo.requireNonBlank(targetMethodName, "targetMethodName");
        JavaClassInfo.requireNonBlank(targetSignature, "targetSignature");
        targetArgumentDescriptor = defaultDescriptor(targetArgumentDescriptor);
        JavaClassInfo.requireNonBlank(variableName, "variableName");
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }

    private static String defaultDescriptor(String descriptor) {
        return descriptor == null || descriptor.isBlank() ? "Ljava/lang/Object;" : descriptor;
    }
}
