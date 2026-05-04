package org.sainm.codeatlas.analyzers.source;

public record MyBatisMapperInterfaceInfo(
        String qualifiedName,
        SourceLocation location) {
    public MyBatisMapperInterfaceInfo {
        JavaClassInfo.requireNonBlank(qualifiedName, "qualifiedName");
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
