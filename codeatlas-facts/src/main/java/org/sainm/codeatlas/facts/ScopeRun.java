package org.sainm.codeatlas.facts;

import org.sainm.codeatlas.symbols.IdentityType;

public record ScopeRun(
        String scopeRunId,
        String analysisRunId,
        String analyzerId,
        String scopeKey,
        RelationFamily relationFamily,
        IdentityType identityType,
        boolean identityTypeDeclared,
        RunStatus status) {
    public ScopeRun {
        requireNonBlank(scopeRunId, "scopeRunId");
        requireNonBlank(analysisRunId, "analysisRunId");
        requireNonBlank(analyzerId, "analyzerId");
        requireNonBlank(scopeKey, "scopeKey");
        if (relationFamily == null) {
            throw new IllegalArgumentException("relationFamily is required");
        }
        if (identityType == null) {
            throw new IllegalArgumentException("identityType is required");
        }
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (status == RunStatus.ROLLED_BACK) {
            throw new IllegalArgumentException("ScopeRun does not support ROLLED_BACK status");
        }
    }

    public ScopeRun(
            String scopeRunId,
            String analysisRunId,
            String analyzerId,
            String scopeKey,
            RelationFamily relationFamily,
            RunStatus status) {
        this(scopeRunId, analysisRunId, analyzerId, scopeKey, relationFamily, defaultIdentityType(relationFamily), false, status);
    }

    public ScopeRun(
            String scopeRunId,
            String analysisRunId,
            String analyzerId,
            String scopeKey,
            RelationFamily relationFamily,
            IdentityType identityType,
            RunStatus status) {
        this(scopeRunId, analysisRunId, analyzerId, scopeKey, relationFamily, identityType, true, status);
    }

    public static ScopeRun planned(
            String scopeRunId,
            String analysisRunId,
            String analyzerId,
            String scopeKey,
            RelationFamily relationFamily) {
        return new ScopeRun(
                scopeRunId,
                analysisRunId,
                analyzerId,
                scopeKey,
                relationFamily,
                defaultIdentityType(relationFamily),
                false,
                RunStatus.PLANNED);
    }

    public static ScopeRun planned(
            String scopeRunId,
            String analysisRunId,
            String analyzerId,
            String scopeKey,
            RelationFamily relationFamily,
            IdentityType identityType) {
        return new ScopeRun(scopeRunId, analysisRunId, analyzerId, scopeKey, relationFamily, identityType, RunStatus.PLANNED);
    }

    public ScopeRun start() {
        requireStatus(RunStatus.PLANNED, RunStatus.RUNNING);
        return withStatus(RunStatus.RUNNING);
    }

    public ScopeRun stage() {
        requireStatus(RunStatus.RUNNING, RunStatus.STAGED);
        return withStatus(RunStatus.STAGED);
    }

    public ScopeRun commit() {
        requireStatus(RunStatus.STAGED, RunStatus.COMMITTED);
        return withStatus(RunStatus.COMMITTED);
    }

    public ScopeRun fail() {
        requireNonTerminal(RunStatus.FAILED);
        return withStatus(RunStatus.FAILED);
    }

    public ScopeRun skip() {
        requireStatus(RunStatus.PLANNED, RunStatus.SKIPPED);
        return withStatus(RunStatus.SKIPPED);
    }

    private ScopeRun withStatus(RunStatus nextStatus) {
        return new ScopeRun(
                scopeRunId,
                analysisRunId,
                analyzerId,
                scopeKey,
                relationFamily,
                identityType,
                identityTypeDeclared,
                nextStatus);
    }

    private static IdentityType defaultIdentityType(RelationFamily relationFamily) {
        if (relationFamily == RelationFamily.FLOW) {
            return IdentityType.FLOW_ID;
        }
        return IdentityType.SYMBOL_ID;
    }

    private void requireStatus(RunStatus expected, RunStatus nextStatus) {
        if (status != expected) {
            throw new IllegalStateException("Cannot transition ScopeRun from " + status + " to " + nextStatus);
        }
    }

    private void requireNonTerminal(RunStatus nextStatus) {
        if (status == RunStatus.COMMITTED || status == RunStatus.FAILED || status == RunStatus.SKIPPED) {
            throw new IllegalStateException("Cannot transition ScopeRun from " + status + " to " + nextStatus);
        }
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
