package org.sainm.codeatlas.analyzers.source;

import java.util.List;

public record StrutsFormBeanInfo(
        String moduleKey,
        String name,
        String type,
        boolean dynamic,
        List<StrutsFormPropertyInfo> properties,
        SourceLocation location) {
    public StrutsFormBeanInfo {
        moduleKey = moduleKey == null ? "" : moduleKey;
        JavaClassInfo.requireNonBlank(name, "name");
        type = type == null ? "" : type;
        properties = List.copyOf(properties == null ? List.of() : properties);
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
