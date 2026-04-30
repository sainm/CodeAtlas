package org.sainm.codeatlas.ai.agent;

public record AgentToolCallDecision(
    boolean allowed,
    String reason
) {
    public AgentToolCallDecision {
        reason = reason == null || reason.isBlank() ? "ok" : reason.trim();
    }

    public static AgentToolCallDecision allow() {
        return new AgentToolCallDecision(true, "ok");
    }

    public static AgentToolCallDecision deny(String reason) {
        return new AgentToolCallDecision(false, reason);
    }
}
