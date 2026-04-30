package org.sainm.codeatlas.analyzers.struts;

import java.util.List;

public record StrutsValidatorField(
    String property,
    List<String> depends
) {
    public StrutsValidatorField {
        property = require(property, "property");
        depends = List.copyOf(depends);
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
