package org.sainm.codeatlas.analyzers.struts;

public record StrutsFormProperty(
    String name,
    String type,
    String initial
) {
    public StrutsFormProperty {
        name = require(name, "name");
        type = type == null || type.isBlank() ? "_unknown" : type.trim();
        initial = initial == null || initial.isBlank() ? null : initial.trim();
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
