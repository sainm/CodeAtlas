package org.sainm.codeatlas.analyzers.source;

import java.util.List;

public record JavaChangedScopeAnalysis(
        JavaSourceAnalysisResult result,
        List<String> analyzedPaths,
        int cacheHits) {
    public JavaChangedScopeAnalysis {
        if (result == null) {
            throw new IllegalArgumentException("result is required");
        }
        analyzedPaths = List.copyOf(analyzedPaths == null ? List.of() : analyzedPaths);
        if (cacheHits < 0) {
            throw new IllegalArgumentException("cacheHits must be non-negative");
        }
    }
}
