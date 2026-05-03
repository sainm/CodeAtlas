package org.sainm.codeatlas.facts;

public record AnalysisRun(
        String analysisRunId,
        String projectId,
        String snapshotId,
        RunStatus status) {
    public AnalysisRun {
        requireNonBlank(analysisRunId, "analysisRunId");
        requireNonBlank(projectId, "projectId");
        requireNonBlank(snapshotId, "snapshotId");
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (status == RunStatus.SKIPPED) {
            throw new IllegalArgumentException("AnalysisRun does not support SKIPPED status");
        }
    }

    public static AnalysisRun planned(String analysisRunId, String projectId, String snapshotId) {
        return new AnalysisRun(analysisRunId, projectId, snapshotId, RunStatus.PLANNED);
    }

    public AnalysisRun start() {
        requireStatus(RunStatus.PLANNED, RunStatus.RUNNING);
        return withStatus(RunStatus.RUNNING);
    }

    public AnalysisRun stage() {
        requireStatus(RunStatus.RUNNING, RunStatus.STAGED);
        return withStatus(RunStatus.STAGED);
    }

    public AnalysisRun commit() {
        requireStatus(RunStatus.STAGED, RunStatus.COMMITTED);
        return withStatus(RunStatus.COMMITTED);
    }

    public AnalysisRun fail() {
        requireNonTerminal(RunStatus.FAILED);
        return withStatus(RunStatus.FAILED);
    }

    public AnalysisRun rollback() {
        requireOneOf(RunStatus.ROLLED_BACK, RunStatus.RUNNING, RunStatus.STAGED, RunStatus.COMMITTED);
        return withStatus(RunStatus.ROLLED_BACK);
    }

    private AnalysisRun withStatus(RunStatus nextStatus) {
        return new AnalysisRun(analysisRunId, projectId, snapshotId, nextStatus);
    }

    private void requireStatus(RunStatus expected, RunStatus nextStatus) {
        if (status != expected) {
            throw new IllegalStateException("Cannot transition AnalysisRun from " + status + " to " + nextStatus);
        }
    }

    private void requireNonTerminal(RunStatus nextStatus) {
        if (status == RunStatus.COMMITTED || status == RunStatus.FAILED || status == RunStatus.ROLLED_BACK) {
            throw new IllegalStateException("Cannot transition AnalysisRun from " + status + " to " + nextStatus);
        }
    }

    private void requireOneOf(RunStatus nextStatus, RunStatus... allowed) {
        for (RunStatus allowedStatus : allowed) {
            if (status == allowedStatus) {
                return;
            }
        }
        throw new IllegalStateException("Cannot transition AnalysisRun from " + status + " to " + nextStatus);
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
