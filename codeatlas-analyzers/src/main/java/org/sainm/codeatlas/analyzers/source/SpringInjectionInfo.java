package org.sainm.codeatlas.analyzers.source;

public record SpringInjectionInfo(
        String ownerQualifiedName,
        String memberName,
        String targetTypeName,
        SpringInjectionKind kind,
        SourceLocation location) {
    public SpringInjectionInfo {
        JavaClassInfo.requireNonBlank(ownerQualifiedName, "ownerQualifiedName");
        JavaClassInfo.requireNonBlank(memberName, "memberName");
        JavaClassInfo.requireNonBlank(targetTypeName, "targetTypeName");
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
