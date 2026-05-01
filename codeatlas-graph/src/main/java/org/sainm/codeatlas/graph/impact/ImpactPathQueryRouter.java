package org.sainm.codeatlas.graph.impact;

import java.util.List;
import java.util.function.Predicate;
import org.sainm.codeatlas.graph.benchmark.FfmActivationDecision;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.store.ActiveFact;

public final class ImpactPathQueryRouter {
    private final ImpactPathQueryEngine defaultEngine = new ImpactPathQueryEngine();
    private final FfmImpactPathQueryEngine ffmEngine = new FfmImpactPathQueryEngine();

    public List<ImpactPath> findUpstreamImpactPaths(
        List<ActiveFact> activeFacts,
        SymbolId changedSymbol,
        Predicate<SymbolId> entrypointPredicate,
        int maxDepth,
        int maxPaths,
        FfmActivationDecision ffmDecision
    ) {
        if (ffmDecision != null && ffmDecision.recommended()) {
            return ffmEngine.findUpstreamImpactPaths(activeFacts, changedSymbol, entrypointPredicate, maxDepth, maxPaths);
        }
        return defaultEngine.findUpstreamImpactPaths(activeFacts, changedSymbol, entrypointPredicate, maxDepth, maxPaths);
    }
}
