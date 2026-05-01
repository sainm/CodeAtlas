package org.sainm.codeatlas.ai.agent;

public interface CodeAtlasAgent {
    AgentProfile profile();

    default AgentType type() {
        return profile().type();
    }

    default AgentAnswer requireValidAnswer(AgentAnswer answer) {
        if (answer == null) {
            throw new IllegalArgumentException("answer is required");
        }
        if (answer.agentType() != type()) {
            throw new IllegalArgumentException("answer agent type does not match profile");
        }
        if (profile().evidenceRequired() && !answer.hasEvidence()) {
            throw new IllegalArgumentException("agent answer requires evidence");
        }
        return answer;
    }
}
