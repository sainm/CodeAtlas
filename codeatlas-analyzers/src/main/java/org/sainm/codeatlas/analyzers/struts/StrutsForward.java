package org.sainm.codeatlas.analyzers.struts;

public record StrutsForward(
    String actionPath,
    String name,
    String path
) {
    public StrutsForward {
        actionPath = require(actionPath, "actionPath");
        name = require(name, "name");
        path = require(path, "path");
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
