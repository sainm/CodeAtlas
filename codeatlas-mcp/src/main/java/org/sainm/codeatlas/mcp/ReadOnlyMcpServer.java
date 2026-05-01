package org.sainm.codeatlas.mcp;

import java.util.List;
import java.util.Map;

public final class ReadOnlyMcpServer {
    private final McpToolRegistry toolRegistry;
    private final McpResourceRegistry resourceRegistry;
    private final McpPromptRegistry promptRegistry;
    private final McpToolCallSafetyGuard toolCallSafetyGuard;
    private final McpInMemoryRateLimiter rateLimiter;
    private final McpAuditLog auditLog;

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
        this(toolRegistry, resourceRegistry, promptRegistry, McpInMemoryRateLimiter.unlimited(), new McpAuditLog());
    }

    public ReadOnlyMcpServer(
        McpToolRegistry toolRegistry,
        McpResourceRegistry resourceRegistry,
        McpPromptRegistry promptRegistry,
        McpInMemoryRateLimiter rateLimiter,
        McpAuditLog auditLog
    ) {
        this.toolRegistry = toolRegistry;
        this.resourceRegistry = resourceRegistry;
        this.promptRegistry = promptRegistry;
        this.toolCallSafetyGuard = new McpToolCallSafetyGuard();
        this.rateLimiter = rateLimiter == null ? McpInMemoryRateLimiter.unlimited() : rateLimiter;
        this.auditLog = auditLog == null ? new McpAuditLog() : auditLog;
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
        return planToolCall(name, arguments, McpRequestContext.anonymousAllowAll());
    }

    public McpToolCallPlan planToolCall(McpToolName name, Map<String, Object> arguments, McpRequestContext context) {
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
        McpRequestContext safeContext = context == null ? McpRequestContext.anonymousAllowAll() : context;
        try {
            if (!toolRegistry.isAllowed(name)) {
                throw new IllegalArgumentException("Tool is not allowed: " + name.value());
            }
            McpToolDescriptor descriptor = toolRegistry.find(name).orElseThrow();
            rateLimiter.checkAllowed(safeContext.principal());
            toolCallSafetyGuard.validate(descriptor, safeArguments, safeContext);
            auditLog.recordAllowed(safeContext, name, safeArguments);
            return new McpToolCallPlan(descriptor, safeArguments);
        } catch (RuntimeException ex) {
            auditLog.recordDenied(safeContext, name, ex.getMessage(), safeArguments);
            throw ex;
        }
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
