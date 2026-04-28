package org.sainm.codeatlas.mcp;

public record McpServerCapabilities(
    boolean tools,
    boolean resources,
    boolean prompts,
    boolean readOnly
) {
    public static McpServerCapabilities defaultReadOnly() {
        return new McpServerCapabilities(true, true, true, true);
    }
}
