package org.sainm.codeatlas.analyzers.source;

public record WebPageEncodingInfo(
        String urlPattern,
        String encoding) {
    public WebPageEncodingInfo {
        JavaClassInfo.requireNonBlank(urlPattern, "urlPattern");
        JavaClassInfo.requireNonBlank(encoding, "encoding");
    }
}
