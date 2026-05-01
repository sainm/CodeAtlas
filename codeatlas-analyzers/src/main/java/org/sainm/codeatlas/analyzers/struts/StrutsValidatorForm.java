package org.sainm.codeatlas.analyzers.struts;

import java.util.List;

public record StrutsValidatorForm(
    String name,
    List<StrutsValidatorField> fields
) {
    public StrutsValidatorForm {
        name = require(name, "name");
        fields = List.copyOf(fields);
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
