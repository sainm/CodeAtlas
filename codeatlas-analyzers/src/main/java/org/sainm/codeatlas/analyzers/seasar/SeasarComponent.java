package org.sainm.codeatlas.analyzers.seasar;

public record SeasarComponent(
    String name,
    String className
) {
    public SeasarComponent {
        name = name == null || name.isBlank() ? className : name.trim();
        className = require(className, "className");
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
