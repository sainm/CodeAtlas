package org.sainm.codeatlas.ai;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class AiRequestAuditLog {
    private final SourceRedactor redactor;
    private final List<AiRequestAuditEvent> events = new CopyOnWriteArrayList<>();

    public AiRequestAuditLog(SourceRedactor redactor) {
        this.redactor = redactor == null ? new SourceRedactor() : redactor;
    }

    public void record(AiPrompt prompt, AiRuntimeConfig config, AiTextResult result) {
        if (prompt == null || config == null || result == null) {
            return;
        }
        events.add(new AiRequestAuditEvent(
            Instant.now(),
            config.provider(),
            config.model(),
            prompt.task(),
            redactor.redactAndTrim(prompt.system(), 4_000),
            redactor.redactAndTrim(prompt.user(), 8_000),
            result.success(),
            result.success() ? "" : redactor.redactAndTrim(result.text(), 1_000)
        ));
    }

    public List<AiRequestAuditEvent> events() {
        return List.copyOf(events);
    }
}
