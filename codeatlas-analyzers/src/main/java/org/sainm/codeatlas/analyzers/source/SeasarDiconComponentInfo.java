package org.sainm.codeatlas.analyzers.source;

public record SeasarDiconComponentInfo(
        String diconPath,
        String namespace,
        String name,
        String className,
        String interfaceName,
        String autoBinding,
        boolean namingCandidate,
        SourceLocation location) {
    public SeasarDiconComponentInfo {
        diconPath = diconPath == null ? "" : diconPath.replace('\\', '/');
        namespace = namespace == null ? "" : namespace;
        name = name == null ? "" : name;
        className = className == null ? "" : className;
        interfaceName = interfaceName == null ? "" : interfaceName;
        autoBinding = autoBinding == null ? "" : autoBinding;
        if (location == null) {
            location = new SourceLocation(diconPath, 1, 1);
        }
    }
}
