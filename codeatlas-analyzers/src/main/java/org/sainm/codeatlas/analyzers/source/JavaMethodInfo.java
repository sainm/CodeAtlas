package org.sainm.codeatlas.analyzers.source;

import java.util.List;

public record JavaMethodInfo(
        String ownerQualifiedName,
        String simpleName,
        String signature,
        String returnTypeName,
        List<String> annotations,
        List<String> modifiers,
        boolean hasBody,
        int endLine,
        SourceLocation location) {
    public JavaMethodInfo(
            String ownerQualifiedName,
            String simpleName,
            String signature,
            String returnTypeName,
            List<String> annotations,
            List<String> modifiers,
            SourceLocation location) {
        this(ownerQualifiedName, simpleName, signature, returnTypeName, annotations, modifiers, false, 0, location);
    }

    public JavaMethodInfo(
            String ownerQualifiedName,
            String simpleName,
            String signature,
            String returnTypeName,
            List<String> annotations,
            List<String> modifiers,
            boolean hasBody,
            SourceLocation location) {
        this(ownerQualifiedName, simpleName, signature, returnTypeName, annotations, modifiers, hasBody, 0, location);
    }

    public JavaMethodInfo {
        JavaClassInfo.requireNonBlank(ownerQualifiedName, "ownerQualifiedName");
        JavaClassInfo.requireNonBlank(simpleName, "simpleName");
        JavaClassInfo.requireNonBlank(signature, "signature");
        returnTypeName = returnTypeName == null ? "" : returnTypeName;
        annotations = JavaClassInfo.copySorted(annotations);
        modifiers = JavaClassInfo.copySorted(modifiers);
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
        if (endLine < 0) {
            throw new IllegalArgumentException("endLine must be non-negative");
        }
    }
}
