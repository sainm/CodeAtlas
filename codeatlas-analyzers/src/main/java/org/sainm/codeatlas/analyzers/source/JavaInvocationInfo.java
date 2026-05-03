package org.sainm.codeatlas.analyzers.source;

public record JavaInvocationInfo(
        String ownerQualifiedName,
        String ownerMethodName,
        String targetQualifiedName,
        String targetSimpleName,
        String targetSignature,
        SourceLocation location) {
    public JavaInvocationInfo {
        ownerQualifiedName = ownerQualifiedName == null ? "" : ownerQualifiedName;
        ownerMethodName = ownerMethodName == null ? "" : ownerMethodName;
        targetQualifiedName = targetQualifiedName == null ? "" : targetQualifiedName;
        JavaClassInfo.requireNonBlank(targetSimpleName, "targetSimpleName");
        targetSignature = targetSignature == null ? "" : targetSignature;
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }

    public JavaInvocationInfo(
            String ownerQualifiedName,
            String ownerMethodName,
            String targetSimpleName,
            String targetSignature,
            SourceLocation location) {
        this(ownerQualifiedName, ownerMethodName, "", targetSimpleName, targetSignature, location);
    }
}
