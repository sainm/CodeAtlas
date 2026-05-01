package org.sainm.codeatlas.analyzers.struts;

import java.util.List;

public record StrutsFormBean(
    String name,
    String type,
    List<StrutsFormProperty> properties
) {
    public StrutsFormBean(String name, String type) {
        this(name, type, List.of());
    }

    public StrutsFormBean {
        name = require(name, "name");
        type = require(type, "type");
        properties = List.copyOf(properties);
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
