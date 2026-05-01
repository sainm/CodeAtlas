package org.sainm.codeatlas.ai;

import java.time.Instant;

public record AiRequestAuditEvent(
    Instant occurredAt,
    AiProviderType provider,
    String model,
    String task,
    String redactedSystemPrompt,
    String redactedUserPrompt,
    boolean success,
    String redactedError
) {
    public AiRequestAuditEvent {
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        provider = provider == null ? AiProviderType.DISABLED : provider;
        model = model == null || model.isBlank() ? "" : model.trim();
        task = task == null || task.isBlank() ? "_unknown" : task.trim();
        redactedSystemPrompt = redactedSystemPrompt == null ? "" : redactedSystemPrompt.trim();
        redactedUserPrompt = redactedUserPrompt == null ? "" : redactedUserPrompt.trim();
        redactedError = redactedError == null ? "" : redactedError.trim();
    }
}
