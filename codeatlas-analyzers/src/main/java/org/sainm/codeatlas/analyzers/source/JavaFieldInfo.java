package org.sainm.codeatlas.analyzers.source;

import java.util.List;

public record JavaFieldInfo(
        String ownerQualifiedName,
        String simpleName,
        String typeName,
        List<String> annotations,
        SourceLocation location) {
    public JavaFieldInfo {
        JavaClassInfo.requireNonBlank(ownerQualifiedName, "ownerQualifiedName");
        JavaClassInfo.requireNonBlank(simpleName, "simpleName");
        typeName = typeName == null ? "" : typeName;
        annotations = JavaClassInfo.copySorted(annotations);
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
