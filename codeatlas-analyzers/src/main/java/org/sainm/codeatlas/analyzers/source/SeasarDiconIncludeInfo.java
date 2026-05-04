package org.sainm.codeatlas.analyzers.source;

public record SeasarDiconIncludeInfo(
        String diconPath,
        String path,
        SourceLocation location) {
    public SeasarDiconIncludeInfo {
        diconPath = diconPath == null ? "" : diconPath.replace('\\', '/');
        path = path == null ? "" : path.replace('\\', '/');
        if (location == null) {
            location = new SourceLocation(diconPath, 1, 1);
        }
    }
}
