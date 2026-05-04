package org.sainm.codeatlas.analyzers.source;

public record DomEventHandlerInfo(
        String pagePath,
        String eventName,
        String relationName,
        String target,
        String httpMethod,
        SourceLocation location) {
    public DomEventHandlerInfo {
        JavaClassInfo.requireNonBlank(pagePath, "pagePath");
        JavaClassInfo.requireNonBlank(eventName, "eventName");
        JavaClassInfo.requireNonBlank(relationName, "relationName");
        JavaClassInfo.requireNonBlank(target, "target");
        httpMethod = httpMethod == null || httpMethod.isBlank() ? "GET" : httpMethod;
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
