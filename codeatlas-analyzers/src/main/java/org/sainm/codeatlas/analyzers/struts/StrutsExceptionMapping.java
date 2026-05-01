package org.sainm.codeatlas.analyzers.struts;

public record StrutsExceptionMapping(
    String scope,
    String type,
    String key,
    String path,
    String handler
) {
    public StrutsExceptionMapping {
        scope = require(scope, "scope");
        type = require(type, "type");
        key = blankToNull(key);
        path = blankToNull(path);
        handler = blankToNull(handler);
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
