package org.sainm.codeatlas.ai.agent;

import java.util.Map;

public final class AgentToolCallGuard {
    public AgentToolCallDecision validate(
        AgentProfile profile,
        AgentToolRegistry registry,
        AgentToolName toolName,
        int completedToolCalls
    ) {
        return validate(profile, registry, toolName, completedToolCalls, Map.of());
    }

    public AgentToolCallDecision validate(
        AgentProfile profile,
        AgentToolRegistry registry,
        AgentToolName toolName,
        int completedToolCalls,
        Map<String, Object> arguments
    ) {
        if (profile == null) {
            return AgentToolCallDecision.deny("agent profile is required");
        }
        if (registry == null) {
            return AgentToolCallDecision.deny("tool registry is required");
        }
        if (toolName == null) {
            return AgentToolCallDecision.deny("tool name is required");
        }
        if (completedToolCalls >= profile.maxToolCalls()) {
            return AgentToolCallDecision.deny("agent tool call limit exceeded");
        }
        if (!profile.allowedTools().contains(toolName)) {
            return AgentToolCallDecision.deny("tool is not allowed for agent profile");
        }
        AgentToolDescriptor descriptor = registry.find(toolName).orElse(null);
        if (descriptor == null) {
            return AgentToolCallDecision.deny("tool is not registered");
        }
        if (!descriptor.readOnly()) {
            return hasWriteConfirmation(arguments)
                ? AgentToolCallDecision.allow()
                : AgentToolCallDecision.deny("write tool requires explicit confirmation");
        }
        if (!registry.isAllowed(toolName)) {
            return AgentToolCallDecision.deny("tool is not registered as read-only");
        }
        return AgentToolCallDecision.allow();
    }

    private boolean hasWriteConfirmation(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return false;
        }
        Object confirmWrite = arguments.get("confirmWrite");
        Object confirmationIntent = arguments.get("confirmationIntent");
        return Boolean.TRUE.equals(confirmWrite) && "ALLOW_WRITE".equals(confirmationIntent);
    }
}
