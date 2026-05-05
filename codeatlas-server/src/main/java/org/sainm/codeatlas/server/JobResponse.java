package org.sainm.codeatlas.server;

public record JobResponse(
        String jobId,
        String reportArtifactId,
        String status) {
}
