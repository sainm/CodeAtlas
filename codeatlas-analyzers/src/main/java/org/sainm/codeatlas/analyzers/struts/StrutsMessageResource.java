package org.sainm.codeatlas.analyzers.struts;

public record StrutsMessageResource(
    String parameter,
    String key,
    String factory,
    boolean nullable
) {
    public StrutsMessageResource {
        parameter = require(parameter, "parameter");
        key = blankToNull(key);
        factory = blankToNull(factory);
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
