package org.sainm.codeatlas.analyzers.source;

public record ClientRequestInfo(String pagePath, String httpMethod, String url, SourceLocation location) {
    public ClientRequestInfo {
        JavaClassInfo.requireNonBlank(pagePath, "pagePath");
        JavaClassInfo.requireNonBlank(httpMethod, "httpMethod");
        JavaClassInfo.requireNonBlank(url, "url");
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
