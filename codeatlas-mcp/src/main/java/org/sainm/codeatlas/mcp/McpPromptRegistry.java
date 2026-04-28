package org.sainm.codeatlas.mcp;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class McpPromptRegistry {
    private final Map<McpPromptName, McpPromptDescriptor> prompts;

    public McpPromptRegistry(List<McpPromptDescriptor> descriptors) {
        prompts = descriptors.stream()
            .collect(Collectors.toUnmodifiableMap(McpPromptDescriptor::name, Function.identity()));
    }

    public static McpPromptRegistry defaultRegistry() {
        return new McpPromptRegistry(Arrays.stream(McpPromptName.values())
            .map(name -> new McpPromptDescriptor(name, description(name), arguments(name), template(name)))
            .toList());
    }

    public List<McpPromptDescriptor> listPrompts() {
        return prompts.values().stream()
            .sorted(Comparator.comparing(prompt -> prompt.name().value()))
            .toList();
    }

    public Optional<McpPromptDescriptor> find(McpPromptName name) {
        return Optional.ofNullable(prompts.get(name));
    }

    private static String description(McpPromptName name) {
        return switch (name) {
            case IMPACT_REVIEW -> "Review an impact report using only static evidence paths.";
            case VARIABLE_TRACE -> "Explain variable source/sink paths with confidence and evidence.";
            case JSP_FLOW_ANALYSIS -> "Explain JSP to backend flow through Action/Controller, Service, Mapper, SQL, and table nodes.";
            case TEST_RECOMMENDATION -> "Recommend focused tests from changed symbols and impact paths.";
        };
    }

    private static List<String> arguments(McpPromptName name) {
        return switch (name) {
            case IMPACT_REVIEW, TEST_RECOMMENDATION -> List.of("reportId");
            case VARIABLE_TRACE -> List.of("projectId", "snapshotId", "symbolId");
            case JSP_FLOW_ANALYSIS -> List.of("projectId", "snapshotId", "jspSymbolId");
        };
    }

    private static String template(McpPromptName name) {
        return switch (name) {
            case IMPACT_REVIEW -> "Summarize report {reportId}. Only cite evidence paths and mark uncertain paths explicitly.";
            case VARIABLE_TRACE -> "Trace variable {symbolId} in project {projectId} snapshot {snapshotId}. Separate sources, sinks, confidence, and evidence.";
            case JSP_FLOW_ANALYSIS -> "Analyze JSP flow {jspSymbolId} in project {projectId} snapshot {snapshotId}. Show each backend path and table touchpoint.";
            case TEST_RECOMMENDATION -> "Recommend tests for report {reportId}. Tie each test to changed symbols and evidence paths.";
        };
    }
}
