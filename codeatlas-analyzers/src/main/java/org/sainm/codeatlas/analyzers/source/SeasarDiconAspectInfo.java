package org.sainm.codeatlas.analyzers.source;

public record SeasarDiconAspectInfo(
        String diconPath,
        String componentName,
        String pointcut,
        String interceptor,
        SourceLocation location) {
    public SeasarDiconAspectInfo {
        diconPath = diconPath == null ? "" : diconPath.replace('\\', '/');
        componentName = componentName == null ? "" : componentName;
        pointcut = pointcut == null ? "" : pointcut;
        interceptor = interceptor == null ? "" : interceptor;
        if (location == null) {
            location = new SourceLocation(diconPath, 1, 1);
        }
    }
}
