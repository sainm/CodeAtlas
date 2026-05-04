package org.sainm.codeatlas.analyzers.source;

public record MyBatisXmlMapperInfo(
        String path,
        String namespace,
        SourceLocation location) {
    public MyBatisXmlMapperInfo {
        JavaClassInfo.requireNonBlank(path, "path");
        JavaClassInfo.requireNonBlank(namespace, "namespace");
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
