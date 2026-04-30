package org.sainm.codeatlas.ai.agent;

import java.util.List;

public record AgentAnswer(
    AgentType agentType,
    String summary,
    List<String> findings,
    List<AgentEvidenceRef> evidence,
    boolean truncated
) {
    public AgentAnswer {
        if (agentType == null) {
            throw new IllegalArgumentException("agentType is required");
        }
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary is required");
        }
        summary = summary.trim();
        findings = findings == null ? List.of() : List.copyOf(findings);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public boolean hasEvidence() {
        return !evidence.isEmpty();
    }
}
