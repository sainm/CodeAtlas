package org.sainm.codeatlas.ai.agent;

import java.time.Instant;

public record AgentAuditEvent(
    String runId,
    AgentType agentType,
    AgentToolName toolName,
    boolean allowed,
    String reason,
    int completedToolCalls,
    Instant occurredAt
) {
    public AgentAuditEvent {
        runId = runId == null || runId.isBlank() ? "_unknown" : runId.trim();
        if (agentType == null) {
            throw new IllegalArgumentException("agentType is required");
        }
        if (toolName == null) {
            throw new IllegalArgumentException("toolName is required");
        }
        reason = reason == null || reason.isBlank() ? "ok" : reason.trim();
        completedToolCalls = Math.max(0, completedToolCalls);
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }
}
