package org.sainm.codeatlas.graph.impact;

import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.EvidenceKey;
import org.sainm.codeatlas.graph.model.FactKey;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.benchmark.FfmActivationDecision;
import org.sainm.codeatlas.graph.store.ActiveFact;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class FastImpactAnalyzer {
    private final ImpactPathQueryEngine pathQueryEngine = new ImpactPathQueryEngine();
    private final ImpactPathQueryRouter pathQueryRouter = new ImpactPathQueryRouter();

    public ImpactReport analyze(
        String reportId,
        String projectId,
        String snapshotId,
        String changeSetId,
        List<ActiveFact> activeFacts,
        List<SymbolId> changedSymbols,
        Predicate<SymbolId> entrypointPredicate,
        int maxDepth,
        int maxPathsPerSymbol
    ) {
        List<ImpactPath> paths = new ArrayList<>();
        for (SymbolId changedSymbol : changedSymbols) {
            paths.addAll(pathQueryEngine.findUpstreamImpactPaths(
                activeFacts,
                changedSymbol,
                entrypointPredicate,
                maxDepth,
                maxPathsPerSymbol
            ));
        }
        return new ImpactReport(
            reportId,
            projectId,
            snapshotId,
            changeSetId,
            ReportDepth.FAST,
            Instant.now(),
            paths,
            evidenceForPaths(activeFacts, paths),
            paths.stream().anyMatch(ImpactPath::truncated)
        );
    }

    public ImpactReport analyzeWithFfmDecision(
        String reportId,
        String projectId,
        String snapshotId,
        String changeSetId,
        List<ActiveFact> activeFacts,
        List<SymbolId> changedSymbols,
        Predicate<SymbolId> entrypointPredicate,
        int maxDepth,
        int maxPathsPerSymbol,
        FfmActivationDecision ffmDecision
    ) {
        List<ImpactPath> paths = new ArrayList<>();
        for (SymbolId changedSymbol : changedSymbols) {
            paths.addAll(pathQueryRouter.findUpstreamImpactPaths(
                activeFacts,
                changedSymbol,
                entrypointPredicate,
                maxDepth,
                maxPathsPerSymbol,
                ffmDecision
            ));
        }
        return new ImpactReport(
            reportId,
            projectId,
            snapshotId,
            changeSetId,
            ReportDepth.FAST,
            Instant.now(),
            paths,
            evidenceForPaths(activeFacts, paths),
            paths.stream().anyMatch(ImpactPath::truncated)
        );
    }

    private List<ImpactEvidence> evidenceForPaths(List<ActiveFact> activeFacts, List<ImpactPath> paths) {
        Map<String, ImpactEvidence> evidence = new LinkedHashMap<>();
        for (ImpactPath path : paths) {
            for (int i = 1; i < path.steps().size(); i++) {
                ImpactPathStep previous = path.steps().get(i - 1);
                ImpactPathStep current = path.steps().get(i);
                RelationType relation = current.incomingRelation();
                if (relation == null) {
                    continue;
                }
                activeFacts.stream()
                    .filter(activeFact -> matches(activeFact.factKey(), previous.symbolId(), relation, current.symbolId()))
                    .forEach(activeFact -> activeFact.evidenceKeys().forEach(key -> evidence.putIfAbsent(
                        key.value(),
                        toEvidence(key, activeFact.confidence())
                    )));
            }
        }
        return List.copyOf(evidence.values());
    }

    private boolean matches(FactKey factKey, SymbolId source, RelationType relation, SymbolId target) {
        return factKey.source().equals(source)
            && factKey.relationType() == relation
            && factKey.target().equals(target);
    }

    private ImpactEvidence toEvidence(EvidenceKey key, Confidence confidence) {
        return new ImpactEvidence(
            key.path(),
            key.lineStart(),
            key.localPath().isBlank() ? key.sourceType().name() : key.localPath(),
            "",
            key.sourceType(),
            confidence
        );
    }
}
