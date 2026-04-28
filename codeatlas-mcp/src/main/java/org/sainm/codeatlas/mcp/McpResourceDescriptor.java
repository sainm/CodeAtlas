package org.sainm.codeatlas.mcp;

public record McpResourceDescriptor(
    McpResourceName name,
    String uriTemplate,
    String description,
    boolean readOnly
) {
    public McpResourceDescriptor {
        if (name == null) {
            throw new IllegalArgumentException("name is required");
        }
        uriTemplate = require(uriTemplate, "uriTemplate");
        description = require(description, "description");
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
