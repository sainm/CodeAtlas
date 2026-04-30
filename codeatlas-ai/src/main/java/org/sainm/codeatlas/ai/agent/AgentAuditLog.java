package org.sainm.codeatlas.ai.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class AgentAuditLog {
    private final List<AgentAuditEvent> events = new ArrayList<>();

    public synchronized AgentAuditEvent record(
        String runId,
        AgentProfile profile,
        AgentToolName toolName,
        AgentToolCallDecision decision,
        int completedToolCalls
    ) {
        if (profile == null) {
            throw new IllegalArgumentException("profile is required");
        }
        if (decision == null) {
            throw new IllegalArgumentException("decision is required");
        }
        AgentAuditEvent event = new AgentAuditEvent(
            runId,
            profile.type(),
            toolName,
            decision.allowed(),
            decision.reason(),
            completedToolCalls,
            Instant.now()
        );
        events.add(event);
        return event;
    }

    public synchronized List<AgentAuditEvent> events() {
        return List.copyOf(events);
    }

    public synchronized List<AgentAuditEvent> eventsForRun(String runId) {
        String normalizedRunId = runId == null || runId.isBlank() ? "_unknown" : runId.trim();
        return events.stream()
            .filter(event -> event.runId().equals(normalizedRunId))
            .toList();
    }
}
