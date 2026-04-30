package org.sainm.codeatlas.ai.agent;

public final class AgentToolCallGuard {
    public AgentToolCallDecision validate(
        AgentProfile profile,
        AgentToolRegistry registry,
        AgentToolName toolName,
        int completedToolCalls
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
        if (!registry.isAllowed(toolName)) {
            return AgentToolCallDecision.deny("tool is not registered as read-only");
        }
        return AgentToolCallDecision.allow();
    }
}
