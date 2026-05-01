package org.sainm.codeatlas.mcp;

import java.time.Instant;
import java.util.Map;

public record McpAuditEvent(
    Instant occurredAt,
    String principal,
    String toolName,
    boolean allowed,
    String reason,
    Map<String, Object> redactedArguments
) {
    public McpAuditEvent {
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        principal = principal == null || principal.isBlank() ? "anonymous" : principal.trim();
        toolName = toolName == null || toolName.isBlank() ? "_unknown" : toolName.trim();
        reason = reason == null ? "" : reason.trim();
        redactedArguments = redactedArguments == null ? Map.of() : Map.copyOf(redactedArguments);
    }
}
