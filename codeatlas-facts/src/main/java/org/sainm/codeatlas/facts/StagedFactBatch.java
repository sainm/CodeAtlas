package org.sainm.codeatlas.facts;

import java.util.List;

public record StagedFactBatch(
        AnalysisRun analysisRun,
        List<ScopeRun> scopeRuns,
        List<FactRecord> facts,
        List<Evidence> evidence) {
    public StagedFactBatch {
        if (analysisRun == null) {
            throw new IllegalArgumentException("analysisRun is required");
        }
        if (analysisRun.status() != RunStatus.STAGED) {
            throw new IllegalArgumentException("analysisRun must be staged");
        }
        scopeRuns = copyRequired(scopeRuns, "scopeRuns");
        facts = copyRequired(facts, "facts");
        evidence = copyRequired(evidence, "evidence");
        for (ScopeRun scopeRun : scopeRuns) {
            if (scopeRun.status() != RunStatus.STAGED) {
                throw new IllegalArgumentException("scopeRuns must be staged");
            }
            if (!scopeRun.analysisRunId().equals(analysisRun.analysisRunId())) {
                throw new IllegalArgumentException("scopeRuns must belong to analysisRun");
            }
        }
    }

    private static <T> List<T> copyRequired(List<T> values, String name) {
        if (values == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return List.copyOf(values);
    }
}
