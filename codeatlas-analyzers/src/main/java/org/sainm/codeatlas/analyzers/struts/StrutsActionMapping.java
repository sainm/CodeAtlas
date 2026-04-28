package org.sainm.codeatlas.analyzers.struts;

public record StrutsActionMapping(
    String path,
    String type,
    String name,
    String scope,
    String input
) {
    public StrutsActionMapping {
        path = require(path, "path");
        type = require(type, "type");
        name = blankToNull(name);
        scope = blankToNull(scope);
        input = blankToNull(input);
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
