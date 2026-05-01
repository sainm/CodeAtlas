package org.sainm.codeatlas.server;

public final class ProjectAccessDeniedException extends RuntimeException {
    private final String projectId;

    public ProjectAccessDeniedException(String projectId) {
        super("Project is not allowed: " + projectId);
        this.projectId = projectId;
    }

    public String projectId() {
        return projectId;
    }
}
