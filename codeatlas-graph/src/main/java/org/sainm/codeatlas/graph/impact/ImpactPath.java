package org.sainm.codeatlas.graph.impact;

import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record ImpactPath(
    SymbolId entrypoint,
    SymbolId changedSymbol,
    List<ImpactPathStep> steps,
    Confidence confidence,
    Set<SourceType> sourceTypes,
    RiskLevel riskLevel,
    String reason,
    boolean truncated
) {
    public ImpactPath {
        Objects.requireNonNull(entrypoint, "entrypoint");
        Objects.requireNonNull(changedSymbol, "changedSymbol");
        Objects.requireNonNull(steps, "steps");
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(sourceTypes, "sourceTypes");
        Objects.requireNonNull(riskLevel, "riskLevel");
        steps = List.copyOf(steps);
        sourceTypes = Set.copyOf(sourceTypes);
        reason = reason == null ? "" : reason.trim();
    }

    public static ImpactPath fromSteps(
        SymbolId entrypoint,
        SymbolId changedSymbol,
        List<ImpactPathStep> steps,
        RiskLevel riskLevel,
        String reason,
        boolean truncated
    ) {
        List<ImpactPathStep> copiedSteps = List.copyOf(steps);
        Confidence pathConfidence = copiedSteps.stream()
            .map(ImpactPathStep::confidence)
            .min(Comparator.comparingInt(Confidence::rank))
            .orElse(Confidence.UNKNOWN);
        Set<SourceType> sourceTypes = copiedSteps.stream()
            .map(ImpactPathStep::sourceType)
            .collect(Collectors.toSet());
        return new ImpactPath(
            entrypoint,
            changedSymbol,
            copiedSteps,
            pathConfidence,
            sourceTypes,
            riskLevel,
            reason,
            truncated
        );
    }
}
