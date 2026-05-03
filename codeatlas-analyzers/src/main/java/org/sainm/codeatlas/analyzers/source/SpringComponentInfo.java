package org.sainm.codeatlas.analyzers.source;

import java.util.List;

public record SpringComponentInfo(
        String qualifiedName,
        SpringComponentKind kind,
        List<String> annotations,
        SourceLocation location) {
    public SpringComponentInfo {
        JavaClassInfo.requireNonBlank(qualifiedName, "qualifiedName");
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        annotations = JavaClassInfo.copySorted(annotations);
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
