package org.sainm.codeatlas.ai.agent;

public final class ImpactAnalysisAgent implements CodeAtlasAgent {
    private final AgentProfile profile;

    public ImpactAnalysisAgent(AgentProfileRegistry profiles) {
        this.profile = profiles.find(AgentType.IMPACT_ANALYSIS)
            .orElseThrow(() -> new IllegalArgumentException("impact analysis profile is required"));
    }

    @Override
    public AgentProfile profile() {
        return profile;
    }
}
