package org.sainm.codeatlas.analyzers.source;

import java.util.Map;

public record JspDirectiveInfo(
        String name,
        Map<String, String> attributes,
        SourceLocation location) {
    public JspDirectiveInfo {
        JavaClassInfo.requireNonBlank(name, "name");
        attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
