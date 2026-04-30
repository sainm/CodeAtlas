package org.sainm.codeatlas.ai.agent;

public record AgentToolDescriptor(
    AgentToolName name,
    String description,
    boolean readOnly,
    int timeoutSeconds
) {
    public AgentToolDescriptor {
        if (name == null) {
            throw new IllegalArgumentException("name is required");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description is required");
        }
        description = description.trim();
        timeoutSeconds = timeoutSeconds <= 0 ? 30 : timeoutSeconds;
    }
}
