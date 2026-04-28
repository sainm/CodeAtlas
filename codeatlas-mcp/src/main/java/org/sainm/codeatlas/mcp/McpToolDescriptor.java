package org.sainm.codeatlas.mcp;

public record McpToolDescriptor(
    McpToolName name,
    String description,
    boolean readOnly,
    int timeoutSeconds,
    String inputSchema
) {
    public McpToolDescriptor {
        if (name == null) {
            throw new IllegalArgumentException("name is required");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description is required");
        }
        description = description.trim();
        timeoutSeconds = timeoutSeconds <= 0 ? 30 : timeoutSeconds;
        inputSchema = inputSchema == null || inputSchema.isBlank() ? "{}" : inputSchema.trim();
    }
}
