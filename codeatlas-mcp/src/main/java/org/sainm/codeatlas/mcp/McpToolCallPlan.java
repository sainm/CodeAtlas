package org.sainm.codeatlas.mcp;

import java.util.Map;

public record McpToolCallPlan(
    McpToolDescriptor descriptor,
    Map<String, Object> arguments
) {
    public McpToolCallPlan {
        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor is required");
        }
        arguments = Map.copyOf(arguments);
    }
}
