package org.sainm.codeatlas.ai.agent;

public final class VariableTraceAgent implements CodeAtlasAgent {
    private final AgentProfile profile;

    public VariableTraceAgent(AgentProfileRegistry profiles) {
        this.profile = profiles.find(AgentType.VARIABLE_TRACE)
            .orElseThrow(() -> new IllegalArgumentException("variable trace profile is required"));
    }

    @Override
    public AgentProfile profile() {
        return profile;
    }
}
