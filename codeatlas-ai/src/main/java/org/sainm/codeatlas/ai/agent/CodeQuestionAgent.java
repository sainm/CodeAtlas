package org.sainm.codeatlas.ai.agent;

public final class CodeQuestionAgent implements CodeAtlasAgent {
    private final AgentProfile profile;

    public CodeQuestionAgent(AgentProfileRegistry profiles) {
        this.profile = profiles.find(AgentType.CODE_QUESTION)
            .orElseThrow(() -> new IllegalArgumentException("code question profile is required"));
    }

    @Override
    public AgentProfile profile() {
        return profile;
    }
}
