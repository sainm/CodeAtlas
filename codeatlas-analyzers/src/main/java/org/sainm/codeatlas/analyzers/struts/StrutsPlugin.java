package org.sainm.codeatlas.analyzers.struts;

import java.util.Map;

public record StrutsPlugin(
    String className,
    Map<String, String> properties
) {
    public StrutsPlugin {
        className = require(className, "className");
        properties = Map.copyOf(properties);
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
