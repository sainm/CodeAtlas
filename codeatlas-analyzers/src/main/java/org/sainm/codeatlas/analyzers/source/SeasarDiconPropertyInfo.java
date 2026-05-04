package org.sainm.codeatlas.analyzers.source;

public record SeasarDiconPropertyInfo(
        String diconPath,
        String componentName,
        String name,
        String expression,
        SourceLocation location) {
    public SeasarDiconPropertyInfo {
        diconPath = diconPath == null ? "" : diconPath.replace('\\', '/');
        componentName = componentName == null ? "" : componentName;
        name = name == null ? "" : name;
        expression = expression == null ? "" : expression;
        if (location == null) {
            location = new SourceLocation(diconPath, 1, 1);
        }
    }
}
