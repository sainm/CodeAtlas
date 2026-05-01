package org.sainm.codeatlas.worker;

import java.time.Duration;
import java.util.concurrent.Callable;

public final class TaiEOptionalWorkerSupervisor {
    private final WorkerTaskRunner taskRunner;

    public TaiEOptionalWorkerSupervisor() {
        this(new WorkerTaskRunner());
    }

    public TaiEOptionalWorkerSupervisor(WorkerTaskRunner taskRunner) {
        if (taskRunner == null) {
            throw new IllegalArgumentException("taskRunner is required");
        }
        this.taskRunner = taskRunner;
    }

    public TaiEOptionalWorkerOutcome runOptional(Callable<TaiEAnalysisImportResult> task, Duration timeout) {
        WorkerTaskResult<TaiEAnalysisImportResult> result = taskRunner.run(task, 1, timeout);
        if (result.status() == WorkerTaskStatus.SUCCEEDED && result.value() != null) {
            return new TaiEOptionalWorkerOutcome(
                WorkerTaskStatus.SUCCEEDED,
                result.value().nodeCount(),
                result.value().factCount(),
                true,
                false,
                "Tai-e deep analysis imported"
            );
        }
        return new TaiEOptionalWorkerOutcome(
            result.status(),
            0,
            0,
            true,
            true,
            result.errorMessage()
        );
    }
}
