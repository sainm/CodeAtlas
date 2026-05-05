package org.sainm.codeatlas.analyzers.source;

public record BeanPropertyFlowInfo(
        String ownerQualifiedName,
        String methodName,
        String methodSignature,
        String sourceObjectName,
        String sourcePropertyName,
        String targetObjectName,
        String targetPropertyName,
        String valueVariableName,
        int line,
        SourceLocation location) {
    public BeanPropertyFlowInfo {
        JavaClassInfo.requireNonBlank(ownerQualifiedName, "ownerQualifiedName");
        JavaClassInfo.requireNonBlank(methodName, "methodName");
        JavaClassInfo.requireNonBlank(methodSignature, "methodSignature");
        JavaClassInfo.requireNonBlank(sourceObjectName, "sourceObjectName");
        JavaClassInfo.requireNonBlank(sourcePropertyName, "sourcePropertyName");
        JavaClassInfo.requireNonBlank(targetObjectName, "targetObjectName");
        JavaClassInfo.requireNonBlank(targetPropertyName, "targetPropertyName");
        JavaClassInfo.requireNonBlank(valueVariableName, "valueVariableName");
        if (line < 0) {
            throw new IllegalArgumentException("line must be non-negative");
        }
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
