package org.sainm.codeatlas.mcp;

import java.util.List;
import java.util.Map;

public final class ReadOnlyMcpServer {
    private final McpToolRegistry toolRegistry;
    private final McpResourceRegistry resourceRegistry;
    private final McpPromptRegistry promptRegistry;
    private final McpToolCallSafetyGuard toolCallSafetyGuard = new McpToolCallSafetyGuard();

    public ReadOnlyMcpServer() {
        this(
            McpToolRegistry.defaultReadOnlyRegistry(),
            McpResourceRegistry.defaultReadOnlyRegistry(),
            McpPromptRegistry.defaultRegistry()
        );
    }

    public ReadOnlyMcpServer(
        McpToolRegistry toolRegistry,
        McpResourceRegistry resourceRegistry,
        McpPromptRegistry promptRegistry
    ) {
        this.toolRegistry = toolRegistry;
        this.resourceRegistry = resourceRegistry;
        this.promptRegistry = promptRegistry;
    }

    public McpServerCapabilities initialize() {
        return McpServerCapabilities.defaultReadOnly();
    }

    public List<McpToolDescriptor> listTools() {
        return toolRegistry.listTools();
    }

    public List<McpResourceDescriptor> listResources() {
        return resourceRegistry.listResources();
    }

    public List<McpPromptDescriptor> listPrompts() {
        return promptRegistry.listPrompts();
    }

    public McpToolCallPlan planToolCall(McpToolName name, Map<String, Object> arguments) {
        if (!toolRegistry.isAllowed(name)) {
            throw new IllegalArgumentException("Tool is not allowed: " + name.value());
        }
        McpToolDescriptor descriptor = toolRegistry.find(name).orElseThrow();
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
        toolCallSafetyGuard.validate(descriptor, safeArguments);
        return new McpToolCallPlan(descriptor, safeArguments);
    }

    public McpResourceDescriptor resource(McpResourceName name) {
        if (!resourceRegistry.isAllowed(name)) {
            throw new IllegalArgumentException("Resource is not allowed: " + name.value());
        }
        return resourceRegistry.find(name).orElseThrow();
    }

    public McpPromptDescriptor prompt(McpPromptName name) {
        return promptRegistry.find(name)
            .orElseThrow(() -> new IllegalArgumentException("Prompt is not allowed: " + name.value()));
    }
}
