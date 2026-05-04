package org.sainm.codeatlas.analyzers.source;

public record StrutsConfigPathInfo(String moduleKey, String path) {
    public StrutsConfigPathInfo {
        moduleKey = moduleKey == null ? "" : moduleKey;
        JavaClassInfo.requireNonBlank(path, "path");
        path = path.replace('\\', '/');
    }
}
