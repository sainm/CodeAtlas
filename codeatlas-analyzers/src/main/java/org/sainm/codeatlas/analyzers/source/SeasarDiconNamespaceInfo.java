package org.sainm.codeatlas.analyzers.source;

public record SeasarDiconNamespaceInfo(
        String diconPath,
        String namespace,
        SourceLocation location) {
    public SeasarDiconNamespaceInfo {
        diconPath = diconPath == null ? "" : diconPath.replace('\\', '/');
        namespace = namespace == null ? "" : namespace;
        if (location == null) {
            location = new SourceLocation(diconPath, 1, 1);
        }
    }
}
