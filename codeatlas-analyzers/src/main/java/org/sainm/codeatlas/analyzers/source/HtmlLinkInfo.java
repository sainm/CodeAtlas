package org.sainm.codeatlas.analyzers.source;

public record HtmlLinkInfo(String pagePath, String href, SourceLocation location) {
    public HtmlLinkInfo {
        JavaClassInfo.requireNonBlank(pagePath, "pagePath");
        JavaClassInfo.requireNonBlank(href, "href");
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
