package org.sainm.codeatlas.ai.agent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class AgentProfileRegistry {
    private final Map<AgentType, AgentProfile> profiles;

    public AgentProfileRegistry(List<AgentProfile> profiles) {
        this.profiles = profiles.stream()
            .collect(Collectors.toUnmodifiableMap(AgentProfile::type, Function.identity()));
    }

    public static AgentProfileRegistry defaultProfiles() {
        return new AgentProfileRegistry(List.of(
            new AgentProfile(
                AgentType.IMPACT_ANALYSIS,
                List.of(
                    AgentToolName.QUERY_PLAN,
                    AgentToolName.SYMBOL_SEARCH,
                    AgentToolName.IMPACT_ANALYZE_DIFF,
                    AgentToolName.GRAPH_FIND_IMPACT_PATHS,
                    AgentToolName.REPORT_GET_IMPACT_REPORT
                ),
                6,
                60,
                true
            ),
            new AgentProfile(
                AgentType.VARIABLE_TRACE,
                List.of(
                    AgentToolName.QUERY_PLAN,
                    AgentToolName.SYMBOL_SEARCH,
                    AgentToolName.VARIABLE_TRACE,
                    AgentToolName.VARIABLE_TRACE_SOURCE,
                    AgentToolName.VARIABLE_TRACE_SINK,
                    AgentToolName.JSP_FIND_BACKEND_FLOW
                ),
                6,
                45,
                true
            ),
            new AgentProfile(
                AgentType.CODE_QUESTION,
                List.of(
                    AgentToolName.QUERY_PLAN,
                    AgentToolName.SYMBOL_SEARCH,
                    AgentToolName.GRAPH_FIND_CALLERS,
                    AgentToolName.GRAPH_FIND_CALLEES,
                    AgentToolName.JSP_FIND_BACKEND_FLOW,
                    AgentToolName.RAG_SEMANTIC_SEARCH,
                    AgentToolName.RAG_ANSWER_DRAFT,
                    AgentToolName.PROJECT_OVERVIEW,
                    AgentToolName.REPORT_GET_IMPACT_REPORT
                ),
                8,
                45,
                true
            )
        ));
    }

    public List<AgentProfile> listProfiles() {
        return profiles.values().stream()
            .sorted(java.util.Comparator.comparing(profile -> profile.type().name()))
            .toList();
    }

    public Optional<AgentProfile> find(AgentType type) {
        return Optional.ofNullable(profiles.get(type));
    }
}
