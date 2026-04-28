package org.sainm.codeatlas.analyzers.jsp;

public record JspInput(
    String name,
    String type,
    int line
) {
    public JspInput {
        name = require(name, "name");
        type = type == null || type.isBlank() ? "text" : type.trim().toLowerCase();
        line = Math.max(0, line);
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
