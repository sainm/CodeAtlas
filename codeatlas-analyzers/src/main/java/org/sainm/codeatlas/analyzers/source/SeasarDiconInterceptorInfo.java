package org.sainm.codeatlas.analyzers.source;

public record SeasarDiconInterceptorInfo(
        String diconPath,
        String name,
        String className,
        SourceLocation location) {
    public SeasarDiconInterceptorInfo {
        diconPath = diconPath == null ? "" : diconPath.replace('\\', '/');
        name = name == null ? "" : name;
        className = className == null ? "" : className;
        if (location == null) {
            location = new SourceLocation(diconPath, 1, 1);
        }
    }
}
