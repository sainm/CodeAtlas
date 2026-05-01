package org.sainm.codeatlas.worker;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public record TaiEWorkerLaunchPlan(
    List<String> command,
    Path workingDirectory,
    Duration timeout
) {
    public TaiEWorkerLaunchPlan {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("command is required");
        }
        if (workingDirectory == null) {
            throw new IllegalArgumentException("workingDirectory is required");
        }
        command = List.copyOf(command);
        timeout = timeout == null || timeout.isZero() || timeout.isNegative() ? Duration.ofMinutes(10) : timeout;
    }
}
