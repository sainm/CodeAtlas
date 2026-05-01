package org.sainm.codeatlas.graph.variable;

import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record VariableTracePath(
    VariableTraceDirection direction,
    SymbolId parameter,
    SymbolId endpoint,
    List<VariableTraceStep> steps,
    Confidence confidence,
    Set<SourceType> sourceTypes,
    boolean truncated
) {
    public VariableTracePath {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(parameter, "parameter");
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(steps, "steps");
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(sourceTypes, "sourceTypes");
        steps = List.copyOf(steps);
        sourceTypes = Set.copyOf(sourceTypes);
    }

    static VariableTracePath fromSteps(
        VariableTraceDirection direction,
        SymbolId parameter,
        SymbolId endpoint,
        List<VariableTraceStep> steps,
        boolean truncated
    ) {
        List<VariableTraceStep> copiedSteps = List.copyOf(steps);
        Confidence pathConfidence = copiedSteps.stream()
            .map(VariableTraceStep::confidence)
            .min(Comparator.comparingInt(Confidence::rank))
            .orElse(Confidence.UNKNOWN);
        Set<SourceType> sourceTypes = copiedSteps.stream()
            .map(VariableTraceStep::sourceType)
            .collect(Collectors.toSet());
        return new VariableTracePath(direction, parameter, endpoint, copiedSteps, pathConfidence, sourceTypes, truncated);
    }
}
