package org.sainm.codeatlas.ai.agent;

import java.util.List;

public record AgentProfile(
    AgentType type,
    List<AgentToolName> allowedTools,
    int maxToolCalls,
    int timeoutSeconds,
    boolean evidenceRequired
) {
    public AgentProfile {
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        if (allowedTools == null || allowedTools.isEmpty()) {
            throw new IllegalArgumentException("allowedTools is required");
        }
        allowedTools = List.copyOf(allowedTools);
        maxToolCalls = maxToolCalls <= 0 ? 4 : maxToolCalls;
        timeoutSeconds = timeoutSeconds <= 0 ? 30 : timeoutSeconds;
    }

    public boolean allows(AgentToolRegistry registry, AgentToolName toolName) {
        return registry != null
            && allowedTools.contains(toolName)
            && registry.isAllowed(toolName);
    }
}
