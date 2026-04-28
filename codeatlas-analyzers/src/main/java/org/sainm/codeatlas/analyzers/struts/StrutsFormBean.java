package org.sainm.codeatlas.analyzers.struts;

public record StrutsFormBean(
    String name,
    String type
) {
    public StrutsFormBean {
        name = require(name, "name");
        type = require(type, "type");
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
