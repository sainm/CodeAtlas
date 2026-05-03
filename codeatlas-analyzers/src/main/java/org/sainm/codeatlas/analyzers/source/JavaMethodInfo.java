package org.sainm.codeatlas.analyzers.source;

import java.util.List;

public record JavaMethodInfo(
        String ownerQualifiedName,
        String simpleName,
        String signature,
        String returnTypeName,
        List<String> annotations,
        SourceLocation location) {
    public JavaMethodInfo {
        JavaClassInfo.requireNonBlank(ownerQualifiedName, "ownerQualifiedName");
        JavaClassInfo.requireNonBlank(simpleName, "simpleName");
        JavaClassInfo.requireNonBlank(signature, "signature");
        returnTypeName = returnTypeName == null ? "" : returnTypeName;
        annotations = JavaClassInfo.copySorted(annotations);
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
