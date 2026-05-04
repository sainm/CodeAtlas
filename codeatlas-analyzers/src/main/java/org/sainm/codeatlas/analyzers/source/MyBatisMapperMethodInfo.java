package org.sainm.codeatlas.analyzers.source;

public record MyBatisMapperMethodInfo(
        String ownerQualifiedName,
        String simpleName,
        String signature,
        SourceLocation location) {
    public MyBatisMapperMethodInfo {
        JavaClassInfo.requireNonBlank(ownerQualifiedName, "ownerQualifiedName");
        JavaClassInfo.requireNonBlank(simpleName, "simpleName");
        JavaClassInfo.requireNonBlank(signature, "signature");
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
