package org.sainm.codeatlas.analyzers.source;

public record FormPropertyReadInfo(
        String ownerQualifiedName,
        String methodName,
        String methodSignature,
        String formVariableName,
        String formTypeName,
        String propertyName,
        int line,
        SourceLocation location) {
    public FormPropertyReadInfo {
        JavaClassInfo.requireNonBlank(ownerQualifiedName, "ownerQualifiedName");
        JavaClassInfo.requireNonBlank(methodName, "methodName");
        JavaClassInfo.requireNonBlank(methodSignature, "methodSignature");
        JavaClassInfo.requireNonBlank(formVariableName, "formVariableName");
        formTypeName = formTypeName == null ? "" : formTypeName;
        JavaClassInfo.requireNonBlank(propertyName, "propertyName");
        if (line < 0) {
            throw new IllegalArgumentException("line must be non-negative");
        }
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
