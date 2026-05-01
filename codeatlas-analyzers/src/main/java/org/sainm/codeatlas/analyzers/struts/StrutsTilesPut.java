package org.sainm.codeatlas.analyzers.struts;

public record StrutsTilesPut(
    String name,
    String value,
    String type
) {
    public StrutsTilesPut {
        name = require(name, "name");
        value = value == null || value.isBlank() ? null : value.trim();
        type = type == null || type.isBlank() ? null : type.trim();
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
