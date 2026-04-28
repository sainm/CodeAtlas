package org.sainm.codeatlas.mcp;

import java.util.List;

public record McpPromptDescriptor(
    McpPromptName name,
    String description,
    List<String> requiredArguments,
    String template
) {
    public McpPromptDescriptor {
        if (name == null) {
            throw new IllegalArgumentException("name is required");
        }
        description = require(description, "description");
        requiredArguments = List.copyOf(requiredArguments);
        template = require(template, "template");
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
