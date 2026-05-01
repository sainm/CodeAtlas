package org.sainm.codeatlas.graph.flow;

import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record GraphFlowPath(
    SymbolId start,
    SymbolId endpoint,
    List<GraphFlowStep> steps,
    Confidence confidence,
    Set<SourceType> sourceTypes,
    boolean truncated
) {
    public GraphFlowPath {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(steps, "steps");
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(sourceTypes, "sourceTypes");
        steps = List.copyOf(steps);
        sourceTypes = Set.copyOf(sourceTypes);
    }

    static GraphFlowPath fromSteps(SymbolId start, SymbolId endpoint, List<GraphFlowStep> steps, boolean truncated) {
        List<GraphFlowStep> copiedSteps = List.copyOf(steps);
        Confidence pathConfidence = copiedSteps.stream()
            .map(GraphFlowStep::confidence)
            .min(Comparator.comparingInt(Confidence::rank))
            .orElse(Confidence.UNKNOWN);
        Set<SourceType> sourceTypes = copiedSteps.stream()
            .map(GraphFlowStep::sourceType)
            .collect(Collectors.toSet());
        return new GraphFlowPath(start, endpoint, copiedSteps, pathConfidence, sourceTypes, truncated);
    }
}
